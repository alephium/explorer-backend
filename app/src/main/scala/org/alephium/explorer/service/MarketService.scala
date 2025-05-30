// Copyright 2018 The Alephium Authors
// This file is part of the alephium project.
//
// The library is free software: you can redistribute it and/or modify
// it under the terms of the GNU Lesser General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// The library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public License
// along with the library. If not, see <http://www.gnu.org/licenses/>.

package org.alephium.explorer.service

import java.util.concurrent.atomic.AtomicBoolean

import scala.collection.immutable.{ArraySeq, ListMap}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

import com.typesafe.scalalogging.StrictLogging
import sttp.client3._
import sttp.client3.asynchttpclient.future.AsyncHttpClientFutureBackend
import sttp.model.{Method, StatusCode, Uri}

import org.alephium.api.model.ApiKey
import org.alephium.explorer.api.model._
import org.alephium.explorer.cache._
import org.alephium.explorer.config.ExplorerConfig
import org.alephium.explorer.util.Scheduler
import org.alephium.json.Json._
import org.alephium.protocol.Hash
import org.alephium.protocol.model.{Address, ContractId}
import org.alephium.util.{discard, Duration, Hex, Math, Service, TimeStamp}

trait MarketService extends Service {
  def getPrices(
      ids: ArraySeq[String],
      chartCurrency: String
  ): Either[String, ArraySeq[Option[Double]]]

  def getExchangeRates(): Either[String, ArraySeq[ExchangeRate]]

  def getPriceChart(symbol: String, currency: String): Either[String, TimedPrices]

  def chartSymbolNames: ListMap[String, String]

  def currencies: ArraySeq[String]
}

object MarketService extends StrictLogging {

  def apply(marketConfig: ExplorerConfig.Market)(implicit
      ec: ExecutionContext
  ): MarketService = marketConfig.mobulaApiKey match {
    case Some(apiKey) => new MarketServiceWithApiKey(marketConfig, apiKey)
    case None         => new MarketServiceWithoutApiKey(marketConfig)
  }

  class MarketServiceWithApiKey(marketConfig: ExplorerConfig.Market, apiKey: ApiKey)(implicit
      val executionContext: ExecutionContext
  ) extends MarketService {

    private val coingeckoBaseUri = marketConfig.coingeckoUri
    private val mobulaBaseUri    = marketConfig.mobulaUri
    private val tokenListUri     = marketConfig.tokenListUri

    private val chartIds: ListMap[String, String] = marketConfig.chartSymbolName

    // scalastyle:off magic.number
    val pricesExpirationTime: Duration      = Duration.ofSecondsUnsafe(30)
    val ratesExpirationTime: Duration       = Duration.ofMinutesUnsafe(5)
    val priceChartsExpirationTime: Duration = Duration.ofMinutesUnsafe(30)
    val tokenListExpirationTime: Duration   = Duration.ofHoursUnsafe(12)

    /*
     * Coingecko rate limit is 15 queries per minutes
     * With the expoential backoff we will retry after
     * 1, 2, 4 and 8 minutes.
     * After that we'll return a Left, this will be retried after the next
     * expiration of the cache.
     */
    def baseDelay: Duration = Duration.ofSecondsUnsafe(60)
    def maxDelay: Duration  = Duration.ofMinutesUnsafe(8)
    def maxRetry: Int       = 4
    // scalastyle:on magic.number

    // scalastyle:off null
    private var backend: SttpBackend[Future, Any] = null
    private val scheduler                         = Scheduler("MARKET_SERVICE_SCHEDULER")

    private val isRunning: AtomicBoolean = new AtomicBoolean(false)

    override def startSelfOnce(): Future[Unit] = {
      backend = AsyncHttpClientFutureBackend()
      isRunning.set(true)
      expireAndReloadCaches()
      Future.unit
    }

    override def stopSelfOnce(): Future[Unit] = {
      isRunning.set(false)
      scheduler.close()
      backend.close()
    }

    override def subServices: ArraySeq[Service] = ArraySeq.empty
    /*
     * We use our `AsyncReloadCache` that always return the latest cached value
     * even if it's expired, like this we guarantee to always return fast a data.
     * We use an `Either` value because we cannot control what's returned by
     * coingecko and it might be that their endoints return something else.
     */
    private val pricesCache: AsyncReloadingCache[Either[String, ArraySeq[Price]]] =
      AsyncReloadingCache[Either[String, ArraySeq[Price]]](
        Left("Price data not fetched"),
        pricesExpirationTime.asScala
      )(_ => getPricesRemote(0))

    private val ratesCache: AsyncReloadingCache[Either[String, ArraySeq[ExchangeRate]]] =
      AsyncReloadingCache[Either[String, ArraySeq[ExchangeRate]]](
        Left("Exchange rate data not fetched"),
        ratesExpirationTime.asScala
      )(_ => getExchangeRatesRemote(0))

    private val priceChartsCache
        : Map[String, AsyncReloadingCache[Either[String, ArraySeq[(TimeStamp, Double)]]]] =
      chartIds.map { case (id, name) =>
        (
          id,
          AsyncReloadingCache[Either[String, ArraySeq[(TimeStamp, Double)]]](
            Left(s"Price chart not fetched for $id"),
            priceChartsExpirationTime.asScala
          )(_ => getPriceChartRemote(name, 0))
        )
      }

    private val tokenListCache: AsyncReloadingCache[Either[String, ArraySeq[TokenList.Entry]]] =
      AsyncReloadingCache[Either[String, ArraySeq[TokenList.Entry]]](
        Left("Token list not fetched"),
        tokenListExpirationTime.asScala
      )(_ => getTokenListRemote(0))

    /*
     * Load data on start
     * Price and Rates only trigger 2 requests
     * for price Chart, it's 1 query per coin.
     * on start, we delay the load of 2 price charts every minute
     * eventually every charts will be in caches.
     */
    def expireAndReloadCaches(): Unit = {
      logger.debug("Load initial price and exchange rate caches")
      // We cant' fetch price without token list, so we make sure to have it before reloading other caches
      discard(
        tokenListCache.expireAndReloadFuture().map { _ =>
          pricesCache.expireAndReload()
        }
      )
      ratesCache.expireAndReload()
      priceChartsCache.grouped(2).zipWithIndex.foreach { case (caches, idx) =>
        scheduler.scheduleOnce(
          s"Expire and reload chart prices for ${caches.map(_._1).mkString(", ")}",
          Duration.ofMinutesUnsafe((1 * idx).toLong).asScala
        )(Future.successful(caches.foreach(_._2.expireAndReload())))
      }
    }

    override def chartSymbolNames: ListMap[String, String] = chartIds
    override def currencies: ArraySeq[String]              = marketConfig.currencies

    def getPrices(
        ids: ArraySeq[String],
        currency: String
    ): Either[String, ArraySeq[Option[Double]]] = {
      for {
        rates <- ratesCache.get()
        usd <- rates
          .find(_.currency == "usd")
          .toRight(s"Cannot find currency usd")
        rate <- rates
          .find(_.currency == currency)
          .toRight(s"Cannot find price for currency $currency")
        prices <- pricesCache.get()
      } yield {
        // Rates from coingecko are based on BTC, but mobula prices are in dollars, so we need to convert them
        ids
          .map(id => prices.find(_.symbol == id).map(price => price.price * rate.value / usd.value))
      }
    }

    private def tokenListToAddresses(tokens: ArraySeq[TokenList.Entry]): ArraySeq[Address] = {
      tokens.map { token =>
        Address.contract(ContractId.unsafe(Hash.unsafe(Hex.unsafe(token.id))))
      }
    }

    private def getPricesRemote(retried: Int): Future[Either[String, ArraySeq[Price]]] = {
      tokenListCache.get() match {
        case Right(tokens) =>
          logger.debug(s"Query mobula `/market/multi-data`, nb of attempts $retried")
          val assets      = tokenListToAddresses(tokens)
          val assetsStr   = assets.map { _.toBase58 }.mkString(",")
          val blockchains = assets.map { _ => "alephium" }.mkString(",")
          request(
            uri"$mobulaBaseUri/market/multi-data?assets=${assetsStr}&blockchains=${blockchains}",
            headers = Map(("Authorizattion", apiKey.value))
          ) { response =>
            handleMobulaPricesRateResponse(response, tokens, retried)
          }
        case Left(error) =>
          Future.successful(Left(s"Token list not fetched at $tokenListUri: $error"))
      }
    }

    private def handleMobulaPricesRateResponse(
        response: Response[Either[String, String]],
        assets: ArraySeq[TokenList.Entry],
        retried: Int
    ): Future[Either[String, ArraySeq[Price]]] = {
      handleResponseAndRetryWithCondition(
        "mobula/price",
        response,
        _.code != StatusCode.Ok,
        retried,
        convertJsonToMobulaPrices(assets),
        getPricesRemote,
        "Cannot fetch prices"
      )
    }

    def getExchangeRates(): Either[String, ArraySeq[ExchangeRate]] = {
      ratesCache.get()
    }

    private def getExchangeRatesRemote(
        retried: Int
    ): Future[Either[String, ArraySeq[ExchangeRate]]] = {
      logger.debug(s"Query coingecko `/exchange_rates`, nb of attempts $retried")
      request(uri"$coingeckoBaseUri/exchange_rates") { response =>
        handleExchangeRateResponse(response, retried)
      }
    }

    private def handleExchangeRateResponse(
        response: Response[Either[String, String]],
        retried: Int
    ): Future[Either[String, ArraySeq[ExchangeRate]]] = {
      handleResponseAndRetryOnTooManyRequests(
        "/exchange_rates",
        response,
        retried,
        convertJsonToExchangeRates,
        getExchangeRatesRemote
      )
    }

    private def handleTokenListResponse(
        response: Response[Either[String, String]],
        retried: Int
    ): Future[Either[String, ArraySeq[TokenList.Entry]]] = {
      handleResponseAndRetryWithCondition(
        tokenListUri,
        response,
        _.code != StatusCode.Ok,
        retried,
        ujson =>
          Try(read[TokenList](ujson).tokens).toEither.left.map { error =>
            s"Cannode decode token list ${error.getMessage}"
          },
        getTokenListRemote(_),
        "Cannot fetch token list"
      )
    }

    def getTokenListRemote(
        retried: Int
    ): Future[Either[String, ArraySeq[TokenList.Entry]]] = {
      request(
        uri"$tokenListUri"
      ) { response =>
        handleTokenListResponse(response, retried)
      }
    }

    def getPriceChart(symbol: String, currency: String): Either[String, TimedPrices] = {
      for {
        rates <- ratesCache.get()
        rate <- rates
          .find(_.currency == currency)
          .toRight(s"Cannot find price for currency $currency")
        cache      <- priceChartsCache.get(symbol).toRight(s"Not price chart for $symbol")
        priceChart <- cache.get()
      } yield {
        val timestamps = priceChart.map { case (ts, _) => ts }
        val values     = priceChart.map { case (_, price) => price * rate.value }
        TimedPrices(timestamps, values)
      }
    }

    def getPriceChartRemote(
        id: String,
        retried: Int
    ): Future[Either[String, ArraySeq[(TimeStamp, Double)]]] = {
      logger.debug(s"Query coingecko `/coins/$id/market_chart`, nb of attempts $retried")
      request(
        uri"$coingeckoBaseUri/coins/$id/market_chart?vs_currency=btc&days=${marketConfig.marketChartDays}"
      ) { response =>
        handleChartResponse(id, response, retried)
      }
    }

    @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
    def request[A](uri: Uri, headers: Map[String, String] = Map.empty)(
        f: Response[Either[String, String]] => Future[Either[String, A]]
    ): Future[Either[String, A]] = {
      if (backend == null || !isRunning.get()) {
        Future.successful(Left("Market service not initialized"))
      } else {
        basicRequest
          .headers(headers)
          .method(Method.GET, uri)
          .send(backend)
          .flatMap(f)
          .recover { case e: Throwable =>
            // If the service is stopped, we don't want to throw an exception
            if (isRunning.get()) {
              throw e
            } else {
              Left(e.getMessage)
            }
          }
      }
    }

    def handleChartResponse(
        id: String,
        response: Response[Either[String, String]],
        retried: Int
    ): Future[Either[String, ArraySeq[(TimeStamp, Double)]]] = {
      handleResponseAndRetryOnTooManyRequests(
        s"/coins/$id/market_chart",
        response,
        retried,
        convertJsonToPriceChart,
        i => getPriceChartRemote(id, i)
      )
    }

    def handleResponseAndRetryOnTooManyRequests[T](
        endpointDescription: String,
        response: Response[Either[String, String]],
        retried: Int,
        reader: ujson.Value => Either[String, T],
        retry: Int => Future[Either[String, T]]
    ): Future[Either[String, T]] =
      handleResponseAndRetryWithCondition(
        endpointDescription,
        response,
        _.code == StatusCode.TooManyRequests,
        retried,
        reader,
        retry,
        "Too many requests"
      )

    def handleResponseAndRetryWithCondition[T](
        endpointDescription: String,
        response: Response[Either[String, String]],
        condition: Response[Either[String, String]] => Boolean,
        retried: Int,
        reader: ujson.Value => Either[String, T],
        retry: Int => Future[Either[String, T]],
        errorMessage: String
    ): Future[Either[String, T]] = {
      if (condition(response) && retried >= maxRetry) {
        val error = s"$errorMessage for $endpointDescription"
        logger.error(error)
        Future.successful(Left(error))
      } else if (condition(response)) {
        val duration = Math.min(baseDelay.timesUnsafe(1L << retried.toLong), maxDelay)
        scheduler.scheduleOnce(s"Retrying $endpointDescription", duration.asScala)(
          retry(retried + 1)
        )
      } else {
        Future.successful(
          response.body.flatMap { body =>
            reader(read[ujson.Value](body))
          }
        )
      }
    }

    private def validateData(
        asset: TokenList.Entry,
        price: Double,
        liquidity: Double
    ): Option[Price] = {
      // If the liquidity is below the minimum, the price is unavailable
      if (liquidity < marketConfig.liquidityMinimum) {
        None
      } else {
        Some(Price(asset.symbol, price, liquidity))
      }
    }

    @SuppressWarnings(Array("org.wartremover.warts.IterableOps"))
    def convertJsonToMobulaPrices(
        assets: ArraySeq[TokenList.Entry]
    )(json: ujson.Value): Either[String, ArraySeq[Price]] = {
      json match {
        case obj: ujson.Obj =>
          obj.value.get("data") match {
            case Some(data: ujson.Obj) =>
              Try {
                ArraySeq.from(assets.flatMap { asset =>
                  val address = tokenListToAddresses(ArraySeq(asset)).head
                  data.value.get(address.toBase58).flatMap { value =>
                    for {
                      price     <- value("price").numOpt
                      liquidity <- value("liquidity").numOpt
                      result    <- validateData(asset, price, liquidity)
                    } yield {
                      result
                    }
                  }
                })
              }.toEither.left.map { error =>
                error.getMessage
              }
            case _ =>
              Left(s"JSON isn't an object: $obj")
          }
        case other =>
          Left(s"JSON isn't an object: $other")
      }
    }

    def convertJsonToExchangeRates(json: ujson.Value): Either[String, ArraySeq[ExchangeRate]] = {
      json match {
        case obj: ujson.Obj =>
          Try {
            obj("rates") match {
              case rates: ujson.Obj =>
                Right(marketConfig.currencies.flatMap { currency =>
                  rates.value.get(currency).map { rate =>
                    ExchangeRate(currency, rate("name").str, rate("unit").str, rate("value").num)
                  }
                })
              case other => Left(s"JSON isn't an object: $other")
            }
          } match {
            case Success(res) => res
            case Failure(error) =>
              Left(error.getMessage)
          }

        case other =>
          Left(s"JSON isn't an object: $other")
      }
    }

    def convertJsonToPriceChart(
        json: ujson.Value
    ): Either[String, ArraySeq[(TimeStamp, Double)]] = {
      json match {
        case obj: ujson.Obj =>
          Try {
            obj("prices") match {
              case prices: ujson.Arr =>
                Right(
                  ArraySeq.from(prices.arr.flatMap {
                    case values: ujson.Arr =>
                      Some((TimeStamp.unsafe(values(0).num.toLong), values(1).num))
                    case _ => None
                  })
                )
              case other => Left(s"JSON isnt' an array: $other")
            }
          } match {
            case Success(res) => res
            case Failure(error) =>
              Left(error.getMessage)
          }

        case other =>
          Left(s"Invalid json object for price chart: $other")
      }
    }
  }

  class MarketServiceWithoutApiKey(marketConfig: ExplorerConfig.Market)(implicit
      val executionContext: ExecutionContext
  ) extends MarketService {

    private val error = Left("No Mobula API key")

    def getPrices(
        ids: ArraySeq[String],
        chartCurrency: String
    ): Either[String, ArraySeq[Option[Double]]] = {
      error
    }

    override def getExchangeRates(): Either[String, ArraySeq[ExchangeRate]] = {
      error
    }

    override def getPriceChart(symbol: String, currency: String): Either[String, TimedPrices] = {
      error
    }

    override def chartSymbolNames: ListMap[String, String] = {
      marketConfig.chartSymbolName
    }

    override def currencies: ArraySeq[String] = {
      marketConfig.currencies
    }

    override def startSelfOnce(): Future[Unit] = {
      logger.error("No Mobula API key, market service disabled")
      Future.unit
    }
    override def stopSelfOnce(): Future[Unit]   = Future.unit
    override def subServices: ArraySeq[Service] = ArraySeq.empty
  }

  final case class TokenList(tokens: ArraySeq[TokenList.Entry])

  object TokenList {
    implicit val readWriter: ReadWriter[TokenList] = macroRW
    final case class Entry(
        id: String,
        symbol: String
    )
    object Entry {
      implicit val readWriter: ReadWriter[Entry] = macroRW
    }
  }
}
