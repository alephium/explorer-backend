package org.alephium.explorer.service

import scala.collection.immutable.{ArraySeq, ListMap}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

import com.typesafe.scalalogging.StrictLogging
import sttp.client3._
import sttp.client3.asynchttpclient.future.AsyncHttpClientFutureBackend
import sttp.model.Method

import org.alephium.explorer.api.model._
import org.alephium.explorer.cache._
import org.alephium.explorer.util.Scheduler
import org.alephium.json.Json._
import org.alephium.util.{Duration, Math}
import sttp.model.StatusCode

trait MarketService {
  def getPrices(ids: List[String], currency: String)(implicit
      ec: ExecutionContext
  ): Future[Either[String, ArraySeq[Price]]]

  def getExchangeRates()(implicit
      ec: ExecutionContext
  ): Future[Either[String, ArraySeq[ExchangeRate]]]

  def getPriceChart(id: String, currency: String)(implicit
      ec: ExecutionContext
  ): Future[Either[String, ArraySeq[(Long, Double)]]]
}

object MarketService extends StrictLogging {
  // TODO add proper types and expose enum in open-api
  // ListMap to preserve order when intialy loading, first ones being the
  // most priority ones
  val ids: ListMap[String, String] = ListMap(
    "alph" -> "alephium",
    "usdc" -> "usd-coin",
    "usdt" -> "tether",
    "wbtc" -> "wrapped-bitcoin",
    "weth" -> "weth",
    "dai"  -> "dai",
    "ayin" -> "ayin"
  )

  val idsR: ListMap[String, String] = ids.map(_.swap)

  // TODO add proper types and expose enum in open-api
  val currencies: ArraySeq[String] = ArraySeq(
    "btc",
    "usd",
    "eur",
    "chf",
    "gbp",
    "idr",
    "vnd",
    "rub",
    "try"
  )

  val baseCurrency: String = "btc"

  object CoinGecko {
    val defaultUri = "https://api.coingecko.com/api/v3"

    def default()(implicit
        ec: ExecutionContext
    ): MarketService = new CoinGecko(defaultUri)
  }

  class CoinGecko(val baseUri: String)(implicit
      ec: ExecutionContext
  ) extends MarketService {

    val pricesExpirationTime: Duration      = Duration.ofMinutesUnsafe(5)
    val ratesExpirationTime: Duration       = Duration.ofMinutesUnsafe(5)
    val priceChartsExpirationTime: Duration = Duration.ofMinutesUnsafe(30)

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

    private val backend   = AsyncHttpClientFutureBackend()
    private val scheduler = Scheduler("MARKET_SERVICE_SCHEDULER")

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
        : ListMap[String, AsyncReloadingCache[Either[String, ArraySeq[(Long, Double)]]]] =
      ids.map { case (id, name) =>
        (
          id,
          AsyncReloadingCache[Either[String, ArraySeq[(Long, Double)]]](
            Left(s"Price chart not fetched for $id"),
            priceChartsExpirationTime.asScala
          )(_ => getPriceChartRemote(name, 0))
        )
      }

    /*
     * Load data on start
     * Price and Rates only trigger 2 requests
     * for price Chart, it's 1 query per coin.
     * on start, we delay the load of 2 price charts every minute
     * eventually every charts will be in caches.
     */
    logger.debug("Load initial price and exchange rate caches")
    pricesCache.expireAndReload()
    ratesCache.expireAndReload()
    priceChartsCache.grouped(2).zipWithIndex.foreach { case (caches, idx) =>
      scheduler.scheduleOnce(
        s"Expire and reload chart prices for ${caches.map(_._1).mkString(", ")}",
        Duration.ofMinutesUnsafe((1 * idx).toLong).asScala
      )(Future.successful(caches.foreach(_._2.expireAndReload())))
    }

    def getPrices(ids: List[String], currency: String)(implicit
        ec: ExecutionContext
    ): Future[Either[String, ArraySeq[Price]]] = {
      Future.successful(
        for {
          rates <- ratesCache.get()
          rate <- rates
            .find(_.currency == currency)
            .toRight(s"Cannot find price for currency $currency")
          prices <- pricesCache.get()
        } yield {
          prices
            .filter(price => ids.contains(price.id))
            .map(price => price.copy(price = price.price * rate.value, currency = currency))
        }
      )
    }

    private def getPricesRemote(retried: Int)(implicit
        ec: ExecutionContext
    ): Future[Either[String, ArraySeq[Price]]] = {
      logger.debug(s"Query coingecko `/price`, nb of attempts $retried")
      basicRequest
        .method(
          Method.GET,
          uri"$baseUri/simple/price?ids=${ids.values.mkString(",")}&vs_currencies=$baseCurrency"
        )
        .send(backend)
        .flatMap { response =>
          handlePricesRateResponse(response, retried)
        }
    }

    private def handlePricesRateResponse(
        response: Response[Either[String, String]],
        retried: Int
    ): Future[Either[String, ArraySeq[Price]]] = {
      handleResponseAndRetry(
        "/simple/prices",
        response,
        retried,
        convertJsonToPrices,
        getPricesRemote
      )
    }

    def getExchangeRates()(implicit
        ec: ExecutionContext
    ): Future[Either[String, ArraySeq[ExchangeRate]]] = {
      Future.successful(ratesCache.get())
    }

    private def getExchangeRatesRemote(retried: Int)(implicit
        ec: ExecutionContext
    ): Future[Either[String, ArraySeq[ExchangeRate]]] = {
      logger.debug(s"Query coingecko `/exchange_rates`, nb of attempts $retried")
      basicRequest
        .method(Method.GET, uri"$baseUri/exchange_rates")
        .send(backend)
        .flatMap { response =>
          handleExchangeRateResponse(response, retried)
        }
    }

    private def handleExchangeRateResponse(
        response: Response[Either[String, String]],
        retried: Int
    ): Future[Either[String, ArraySeq[ExchangeRate]]] = {
      handleResponseAndRetry(
        "/exchange_rates",
        response,
        retried,
        convertJsonToExchangeRates,
        getExchangeRatesRemote
      )
    }

    def getPriceChart(id: String, currency: String)(implicit
        ec: ExecutionContext
    ): Future[Either[String, ArraySeq[(Long, Double)]]] = {
      Future.successful(
        for {
          rates <- ratesCache.get()
          rate <- rates
            .find(_.currency == currency)
            .toRight(s"Cannot find price for currency $currency")
          cache      <- priceChartsCache.get(id).toRight(s"Not price chart for $id")
          priceChart <- cache.get()
        } yield {
          priceChart.map { case (ts, price) =>
            (ts, price * rate.value)
          }
        }
      )
    }

    def getPriceChartRemote(id: String, retried: Int)(implicit
        ec: ExecutionContext
    ): Future[Either[String, ArraySeq[(Long, Double)]]] = {
      logger.debug(s"Query coingecko `/coins/$id/market_chart`, nb of attempts $retried")
      basicRequest
        .method(Method.GET, uri"$baseUri/coins/$id/market_chart?vs_currency=$baseCurrency&days=365")
        .send(backend)
        .flatMap { response =>
          handleChartResponse(id, response, retried)
        }
    }

    def handleChartResponse(
        id: String,
        response: Response[Either[String, String]],
        retried: Int
    ): Future[Either[String, ArraySeq[(Long, Double)]]] = {
      handleResponseAndRetry(
        s"/coins/$id/market_chart",
        response,
        retried,
        convertJsonToPriceChart,
        i => getPriceChartRemote(id, i)
      )
    }

    def handleResponseAndRetry[T](
        endpointDescription: String,
        response: Response[Either[String, String]],
        retried: Int,
        reader: ujson.Value => Either[String, T],
        retry: Int => Future[Either[String, T]]
    ): Future[Either[String, T]] = {
      if (response.code == StatusCode.TooManyRequests && retried >= maxRetry) {
        Future.successful(Left(s"Too many requests for $endpointDescription"))
      } else if (response.code == StatusCode.TooManyRequests) {
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

    def convertJsonToPrices(json: ujson.Value): Either[String, ArraySeq[Price]] = {
      json match {
        case obj: ujson.Obj =>
          Try {
            ArraySeq.from(obj.value.flatMap { case (name, value) =>
              idsR.get(name).map { id =>
                Price(id, name, value(baseCurrency).num, baseCurrency)
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
                Right(currencies.flatMap { currency =>
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

    def convertJsonToPriceChart(json: ujson.Value): Either[String, ArraySeq[(Long, Double)]] = {
      json match {
        case obj: ujson.Obj =>
          Try {
            obj("prices") match {
              case prices: ujson.Arr =>
                Right(
                  ArraySeq.from(prices.arr.flatMap {
                    case values: ujson.Arr =>
                      Some((values(0).num.toLong, values(1).num))
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
}
