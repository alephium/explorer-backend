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

package org.alephium.explorer.service.market

import java.util.concurrent.atomic.AtomicBoolean

import scala.collection.immutable.{ArraySeq, ListMap}
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

import com.typesafe.scalalogging.StrictLogging
import sttp.client3._
import sttp.client3.asynchttpclient.future.AsyncHttpClientFutureBackend
import sttp.model.{Method, StatusCode, Uri}

import org.alephium.api.UtilJson._
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

// scalastyle:off number.of.methods
object MarketService extends StrictLogging {

  def apply(marketConfig: ExplorerConfig.Market)(implicit
      ec: ExecutionContext
  ): MarketService = new MarketServiceImpl(marketConfig, marketConfig.mobulaApiKey)

  class MarketServiceImpl(marketConfig: ExplorerConfig.Market, apiKeyOpt: Option[ApiKey])(implicit
      val executionContext: ExecutionContext
  ) extends MarketService {

    private val coingeckoBaseUri = marketConfig.coingeckoUri
    private val mobulaBaseUri    = marketConfig.mobulaUri
    private val tokenListUri     = marketConfig.tokenListUri

    private val chartIds: ListMap[String, String] = marketConfig.chartSymbolName

    private def symbolNames: ListMap[String, String] = marketConfig.symbolName
    private val symbolNamesR                         = symbolNames.map(_.swap)

    private val baseCurrency: String = "usd"

    // scalastyle:off magic.number
    val pricesExpirationTime: FiniteDuration      = marketConfig.pricesExpirationTime
    val ratesExpirationTime: FiniteDuration       = marketConfig.ratesExpirationTime
    val priceChartsExpirationTime: FiniteDuration = marketConfig.priceChartsExpirationTime
    val tokenListExpirationTime: FiniteDuration   = marketConfig.tokenListExpirationTime

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
    private[market] val mobulaPricesCache
        : AsyncReloadingCache[Either[String, ArraySeq[MobulaPrice]]] =
      AsyncReloadingCache[Either[String, ArraySeq[MobulaPrice]]](
        Left("Price data not fetched for Mobula"),
        pricesExpirationTime
      )(_ => getMobulaPricesRemote(0))

    private val coingeckoPricesCache
        : AsyncReloadingCache[Either[String, ArraySeq[CoingeckoPrice]]] =
      AsyncReloadingCache[Either[String, ArraySeq[CoingeckoPrice]]](
        Left("Price data not fetched for Coingecko"),
        pricesExpirationTime
      )(_ => getCoingeckoPricesRemote(0))

    private val ratesCache: AsyncReloadingCache[Either[String, ArraySeq[ExchangeRate]]] =
      AsyncReloadingCache[Either[String, ArraySeq[ExchangeRate]]](
        Left("Exchange rate data not fetched"),
        ratesExpirationTime
      )(_ => getExchangeRatesRemote(0))

    private val priceChartsCache
        : Map[String, AsyncReloadingCache[Either[String, ArraySeq[(TimeStamp, Double)]]]] =
      chartIds.map { case (id, name) =>
        (
          id,
          AsyncReloadingCache[Either[String, ArraySeq[(TimeStamp, Double)]]](
            Left(s"Price chart not fetched for $id"),
            priceChartsExpirationTime
          )(_ => getPriceChartRemote(name, 0))
        )
      }

    private[service] val tokenListCache: AsyncReloadingCache[Either[String, TokenList]] =
      AsyncReloadingCache[Either[String, TokenList]](
        Left("Token list not fetched"),
        tokenListExpirationTime
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
          mobulaPricesCache.expireAndReload()
          coingeckoPricesCache.expireAndReload()
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

    /** Get prices from the two caches and merge them. We favor mobula prices over coingecko prices,
      * as they are more accurate. If the price is not available, it will return None.
      */
    private def getPriceCache(): Either[String, ArraySeq[Price]] = {
      (mobulaPricesCache.get(), coingeckoPricesCache.get()) match {
        case (Right(mobula), Right(coingecko)) =>
          Right(
            mobula
              .concat[Price](coingecko)
              .groupBy(_.symbol)
              .view
              .mapValues(
                _.headOption
              ) // We favor mobula prices over coingecko, as they are more accurate
              .values
              .flatten
              .to(ArraySeq)
          )

        case (Right(mobula), Left(_))    => Right(mobula)
        case (Left(_), Right(coingecko)) => Right(coingecko)
        case (Left(mobulaError), Left(coingeckoError)) =>
          Left(s"Failed to fetch prices: $mobulaError, $coingeckoError")
      }
    }

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
        prices <- getPriceCache()
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

    // This is used to validate the token list freshness. Only used in `getTokenList`
    private var lastValidatedTokenPriceTime = TimeStamp.zero

    /** Get the token list from the cache, if it's not fresh, we return the tokens that have valid
      * prices.
      *
      * The idea is to avoid fetching prices for all tokens, while most of them don't have a price
      * or enough liquidity. So every time we fetch the token list, we re-check if the prices are
      * now valid, otherwise we recompute the prices for the current tokens with valid prices.
      */
    private def getTokenList(): Either[String, ArraySeq[TokenList.Entry]] = {
      (tokenListCache.get(), mobulaPricesCache.get()) match {
        case (Right(tokenList), Right(prices)) =>
          if (tokenList.fetchedAt.exists(at => lastValidatedTokenPriceTime.isBefore(at))) {
            // Token list is fresh, we need to validate prices for all tokens
            lastValidatedTokenPriceTime = TimeStamp.now()
            Right(tokenList.tokens)
          } else {
            // Token list is not fresh, we recompute price of current validated tokens
            Right(prices.map(_.asset))
          }
        case (Right(tokenList), Left(_)) =>
          // Prices aren't fetched yet, we return the token list as is
          lastValidatedTokenPriceTime = TimeStamp.now()
          Right(tokenList.tokens)
        case (Left(error), _) =>
          Left(error)
      }
    }

    private def getMobulaPricesRemote(
        retried: Int
    ): Future[Either[String, ArraySeq[MobulaPrice]]] = {
      apiKeyOpt match {
        case Some(apiKey) =>
          getTokenList() match {
            case Right(tokens) =>
              logger.debug(s"Query mobula `/market/multi-data`, nb of attempts $retried")
              val assets      = tokenListToAddresses(tokens)
              val assetsStr   = assets.map { _.toBase58 }.mkString(",")
              val blockchains = assets.map { _ => "alephium" }.mkString(",")
              request(
                uri"$mobulaBaseUri/market/multi-data?assets=${assetsStr}&blockchains=${blockchains}",
                headers = Map(("Authorization", apiKey.value))
              ) { response =>
                handleMobulaPricesRateResponse(response, tokens, retried)
              }
            case Left(error) =>
              Future.successful(Left(s"Token list not fetched at $tokenListUri: $error"))
          }

        case None =>
          Future.successful(Left("No Mobula API key"))
      }
    }

    private def getCoingeckoPricesRemote(
        retried: Int
    ): Future[Either[String, ArraySeq[CoingeckoPrice]]] = {
      logger.debug(s"Query coingecko `/price`, nb of attempts $retried")
      request(
        uri"$coingeckoBaseUri/simple/price?ids=${symbolNames.values.mkString(",")}&vs_currencies=$baseCurrency"
      ) { response =>
        handleCoingeckoPricesRateResponse(response, retried)
      }
    }

    private def handleMobulaPricesRateResponse(
        response: Response[Either[String, String]],
        assets: ArraySeq[TokenList.Entry],
        retried: Int
    ): Future[Either[String, ArraySeq[MobulaPrice]]] = {
      handleResponseAndRetryWithCondition(
        "mobula/price",
        response,
        _.code != StatusCode.Ok,
        retried,
        convertJsonToMobulaPrices(assets),
        getMobulaPricesRemote,
        "Cannot fetch prices"
      )
    }

    private def handleCoingeckoPricesRateResponse(
        response: Response[Either[String, String]],
        retried: Int
    ): Future[Either[String, ArraySeq[CoingeckoPrice]]] = {
      handleResponseAndRetryWithCondition(
        "coingecko/price",
        response,
        _.code != StatusCode.Ok,
        retried,
        convertJsonToCoingeckoPrices,
        getCoingeckoPricesRemote,
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
    ): Future[Either[String, TokenList]] = {
      handleResponseAndRetryWithCondition(
        tokenListUri,
        response,
        _.code != StatusCode.Ok,
        retried,
        ujson =>
          Try(read[TokenList](ujson)) match {
            case Success(tokenList) =>
              Right(tokenList.copy(fetchedAt = Some(TimeStamp.now())))
            case Failure(error) =>
              Left(s"Cannode decode token list ${error.getMessage}")
          },
        getTokenListRemote(_),
        "Cannot fetch token list"
      )
    }

    def getTokenListRemote(
        retried: Int
    ): Future[Either[String, TokenList]] = {
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

    private def validateMobulaData(
        asset: TokenList.Entry,
        price: Double,
        liquidity: Double
    ): Option[MobulaPrice] = {
      // If the liquidity is below the minimum, the price is unavailable
      // Or if the price is 0, we also consider it unavailable, this might happen if
      // the api has an issue.
      if (liquidity < marketConfig.liquidityMinimum || price == 0.0) {
        None
      } else {
        Some(MobulaPrice(asset, price, liquidity))
      }
    }

    @SuppressWarnings(Array("org.wartremover.warts.IterableOps"))
    private def convertJsonToMobulaPrices(
        assets: ArraySeq[TokenList.Entry]
    )(json: ujson.Value): Either[String, ArraySeq[MobulaPrice]] = {
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
                      result    <- validateMobulaData(asset, price, liquidity)
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

    @SuppressWarnings(Array("org.wartremover.warts.IterableOps"))
    private def convertJsonToCoingeckoPrices(
        json: ujson.Value
    ): Either[String, ArraySeq[CoingeckoPrice]] = {
      json match {
        case obj: ujson.Obj =>
          Try {
            ArraySeq.from(obj.value.flatMap { case (name, value) =>
              symbolNamesR.get(name).map { id =>
                CoingeckoPrice(id, value(baseCurrency).num)
              }
            })
          }.toEither.left.map { error =>
            error.getMessage
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

  @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
  final private[market] case class TokenList(
      tokens: ArraySeq[TokenList.Entry],
      fetchedAt: Option[TimeStamp] = None
  )

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

  sealed private[market] trait Price {
    def symbol: String
    def price: Double
  }

  final private[market] case class MobulaPrice(
      asset: TokenList.Entry,
      price: Double,
      liquidity: Double
  ) extends Price {
    val symbol: String = asset.symbol
  }

  final private case class CoingeckoPrice(symbol: String, price: Double) extends Price
}
