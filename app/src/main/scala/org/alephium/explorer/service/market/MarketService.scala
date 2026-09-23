// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

//scalastyle:off file.size.limit
package org.alephium.explorer.service.market

import java.util.concurrent.atomic.AtomicBoolean

import scala.collection.immutable.{ArraySeq, ListMap}
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

import com.typesafe.scalalogging.StrictLogging
import sttp.client4._
import sttp.client4.httpclient.HttpClientFutureBackend
import sttp.model.{Method, StatusCode, Uri}

import org.alephium.api.UtilJson._
import org.alephium.api.model.{ApiKey, ContractState, Val, ValByteVec, ValU256}
import org.alephium.explorer.api.model._
import org.alephium.explorer.cache._
import org.alephium.explorer.config.ExplorerConfig
import org.alephium.explorer.config.ExplorerConfig.PowfiPool
import org.alephium.explorer.foldFutures
import org.alephium.explorer.service.BlockFlowClient
import org.alephium.explorer.util.Scheduler
import org.alephium.json.Json._
import org.alephium.protocol.Hash
import org.alephium.protocol.model.{Address, ContractId}
import org.alephium.util.{discard, AVector, Duration, Hex, Math, Service, TimeStamp}

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

  def apply(marketConfig: ExplorerConfig.Market, blockFlowClient: BlockFlowClient)(implicit
      ec: ExecutionContext
  ): MarketService = new MarketServiceImpl(marketConfig, marketConfig.mobulaApiKey, blockFlowClient)

  class MarketServiceImpl(
      marketConfig: ExplorerConfig.Market,
      apiKeyOpt: Option[ApiKey],
      blockFlowClient: BlockFlowClient
  )(implicit
      val executionContext: ExecutionContext
  ) extends MarketService {

    private val coingeckoBaseUri = marketConfig.coingeckoUri
    private val mobulaBaseUri    = marketConfig.mobulaUri
    private val tokenListUri     = marketConfig.tokenListUri

    private val chartIds: ListMap[String, String] = marketConfig.chartSymbolName

    private def symbolNames: ListMap[String, String] = marketConfig.symbolName
    private val symbolNamesR: Map[String, Iterable[String]] =
      symbolNames.groupMap { case (_, v) => v } { case (k, _) => k }

    private val baseCurrency: String = "usd"

    // scalastyle:off magic.number
    val mobulaPricesExpirationTime: FiniteDuration    = marketConfig.mobulaPricesExpirationTime
    val coingeckoPricesExpirationTime: FiniteDuration = marketConfig.coingeckoPricesExpirationTime
    val ratesExpirationTime: FiniteDuration           = marketConfig.ratesExpirationTime
    val priceChartsExpirationTime: FiniteDuration     = marketConfig.priceChartsExpirationTime
    val tokenListExpirationTime: FiniteDuration       = marketConfig.tokenListExpirationTime

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

    private var backend: Option[Backend[Future]] = None
    private val scheduler                        = Scheduler("MARKET_SERVICE_SCHEDULER")

    private val isRunning: AtomicBoolean = new AtomicBoolean(false)

    override def startSelfOnce(): Future[Unit] = {
      backend = Some(HttpClientFutureBackend())
      isRunning.set(true)
      expireAndReloadCaches()
      Future.unit
    }

    override def stopSelfOnce(): Future[Unit] = {
      isRunning.set(false)
      scheduler.close()
      val closeResult = backend.map(_.close()).getOrElse(Future.unit)
      backend = None
      closeResult
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
        mobulaPricesExpirationTime
      )(_ => getMobulaPricesRemote(0))

    private val coingeckoPricesCache
        : AsyncReloadingCache[Either[String, ArraySeq[CoingeckoPrice]]] =
      AsyncReloadingCache[Either[String, ArraySeq[CoingeckoPrice]]](
        Left("Price data not fetched for Coingecko"),
        coingeckoPricesExpirationTime
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

    private val powfiPricesCache: AsyncReloadingCache[ArraySeq[PowfiPrice]] =
      AsyncReloadingCache[ArraySeq[PowfiPrice]](ArraySeq.empty, 5.minutes)(_ =>
        getPowfiPricesRemote()
      )

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
          powfiPricesCache.expireAndReload()
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

    private val coingeckoPrioritySymbols: Set[String] =
      marketConfig.coingeckoPrioritySymbols.toSet

    private def selectPrice(prices: Iterable[Price]): Option[Price] = {
      val firstPrice = prices.headOption
      if (firstPrice.exists(price => coingeckoPrioritySymbols.contains(price.symbol))) {
        prices
          .collectFirst { case price: CoingeckoPrice => price }
          .orElse(firstPrice)
      } else {
        firstPrice
      }
    }

    /** Get prices from the two caches and merge them. We favor Mobula prices over CoinGecko prices,
      * except for symbols configured with CoinGecko priority. If the price is not available, it
      * will return None.
      */
    private def getPriceCache(): Either[String, ArraySeq[Price]] = {
      (mobulaPricesCache.get(), coingeckoPricesCache.get()) match {
        case (Right(mobula), Right(coingecko)) =>
          Right(
            mobula
              .concat[Price](coingecko)
              .groupBy(_.symbol)
              .view
              .mapValues(selectPrice)
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
        val powfiPrices = powfiPricesCache.get()
        def marketPrice(symbol: String): Option[Double] =
          if (marketConfig.usdPeggedSymbols.contains(symbol)) {
            Some(1.0)
          } else {
            prices.find(_.symbol == symbol).map(_.price)
          }
        def usdPrice(symbol: String): Option[Double] =
          if (marketConfig.powfiPools.contains(symbol)) {
            for {
              powfi <- powfiPrices.find(_.symbol == symbol)
              quote <- marketPrice(powfi.quoteSymbol)
            } yield powfi.price * quote
          } else {
            marketPrice(symbol)
          }
        // Rates from coingecko are based on BTC, but mobula prices are in dollars, so we need to convert them
        ids.map(id => usdPrice(id).map(_ * rate.value / usd.value))
      }
    }

    private def getPowfiPricesRemote(): Future[ArraySeq[PowfiPrice]] = {
      val tokens = tokenListCache.get().map(_.tokens)
      Future
        .sequence(ArraySeq.from(marketConfig.powfiPools).map { case (symbol, pool) =>
          blockFlowClient
            .fetchContractState(Address.contract(pool.pool))
            .map(state => tokens.flatMap(powfiPrice(symbol, pool.`type`, state, _)))
            .recover { case error => Left(error.getMessage) }
            .map {
              case Right(price) => Some(price)
              case Left(error) =>
                logger
                  .error(s"Cannot price $symbol from PowFi pool ${pool.pool.toHexString}: $error")
                None
            }
        })
        .map(_.flatten)
    }

    private val q96: Double = math.pow(2, 96) // scalastyle:ignore magic.number

    private def powfiPrice(
        symbol: String,
        poolType: PowfiPool.Type,
        state: ContractState,
        tokens: ArraySeq[TokenList.Entry]
    ): Either[String, PowfiPrice] = {
      val (token0Index, rawPrice) = poolType match {
        case PowfiPool.Clmm =>
          (6, u256Field(state.mutFields, 1).map(sqrtPriceX96 => math.pow(sqrtPriceX96 / q96, 2)))
        case PowfiPool.Cpmm =>
          val rawPrice = for {
            reserve0 <- u256Field(state.mutFields, 1)
            reserve1 <- u256Field(state.mutFields, 2)
          } yield reserve1 / reserve0
          (1, rawPrice)
      }
      for {
        token0 <- tokenField(state.immFields, token0Index, tokens)
        token1 <- tokenField(state.immFields, token0Index + 1, tokens)
        raw    <- rawPrice
        price0 = raw * math.pow(10, (token0.decimals - token1.decimals).toDouble)
        price <-
          if (token0.symbol == symbol) {
            Right(PowfiPrice(symbol, token1.symbol, price0))
          } else if (token1.symbol == symbol) {
            Right(PowfiPrice(symbol, token0.symbol, 1 / price0))
          } else {
            Left(s"$symbol is not in the pool")
          }
        _ <- Either.cond(price.price > 0 && !price.price.isInfinite, (), s"Invalid price $price")
      } yield price
    }

    private def u256Field(fields: AVector[Val], index: Int): Either[String, Double] =
      fields.get(index) match {
        case Some(ValU256(value)) => Right(value.toBigInt.doubleValue)
        case other                => Left(s"Field $index isn't a U256: $other")
      }

    private def tokenField(
        fields: AVector[Val],
        index: Int,
        tokens: ArraySeq[TokenList.Entry]
    ): Either[String, TokenList.Entry] =
      fields.get(index) match {
        case Some(ValByteVec(bytes)) =>
          val id = Hex.toHexString(bytes)
          tokens.find(_.id == id).toRight(s"Token $id isn't in the token list")
        case other => Left(s"Field $index isn't a token id: $other")
      }

    private def tokenToAddress(token: TokenList.Entry): Address = {
      Address.contract(ContractId.unsafe(Hash.unsafe(Hex.unsafe(token.id))))
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

    private def batchTokens(
        tokens: ArraySeq[TokenList.Entry],
        batchSize: Int
    ): ArraySeq[ArraySeq[TokenList.Entry]] = {
      ArraySeq.from(tokens.grouped(batchSize).toSeq.map(ArraySeq.from))
    }

    private def getMobulaPricesRemote(
        retried: Int
    ): Future[Either[String, ArraySeq[MobulaPrice]]] = {
      apiKeyOpt match {
        case Some(apiKey) =>
          getTokenList() match {
            case Right(tokens) =>
              logger.debug(s"Query mobula `/market/multi-data`, nb of attempts $retried")

              val batches = batchTokens(tokens, marketConfig.mobulaMaxTokensPerRequest)

              val batchFutures = foldFutures(batches) { batch =>
                val mobulaPriceRequest = MobulaPriceRequest(
                  items = ArraySeq.from(
                    batch.map { token =>
                      MobulaPriceRequestAsset(tokenToAddress(token).toBase58, "Alephium")
                    }
                  )
                )

                requestPost(
                  uri"$mobulaBaseUri/token/price",
                  writeJs(mobulaPriceRequest),
                  headers = Map(("Authorization", apiKey.value))
                )(response => handleMobulaPricesRateResponse(response, batch, retried))
              }

              combineBatchResults(batchFutures)

            case Left(error) =>
              Future.successful(Left(s"Token list not fetched at $mobulaBaseUri: $error"))
          }

        case None =>
          Future.successful(Left("No Mobula API key"))
      }
    }

    private def combineBatchResults(
        batchFutures: Future[Seq[Either[String, ArraySeq[MobulaPrice]]]]
    ): Future[Either[String, ArraySeq[MobulaPrice]]] = {
      batchFutures.map { batchResults =>
        val successes = batchResults.collect { case Right(prices) => prices }
        val errors    = batchResults.collect { case Left(error) => error }

        if (errors.isEmpty) {
          Right(successes.flatten.to(ArraySeq))
        } else {
          logger.error(s"Errors occurred while fetching Mobula prices: ${errors.mkString("; ")}")
          Left(errors.mkString("; "))
        }
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
        !_.code.isSuccess,
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
        !_.code.isSuccess,
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
        !_.code.isSuccess,
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
      backend match {
        case Some(backend) if isRunning.get() =>
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
        case _ =>
          Future.successful(Left("Market service not initialized"))
      }
    }

    def requestPost[A](
        uri: Uri,
        payload: ujson.Value,
        headers: Map[String, String]
    )(
        f: Response[Either[String, String]] => Future[Either[String, A]]
    ): Future[Either[String, A]] = {
      backend match {
        case Some(backend) if isRunning.get() =>
          basicRequest
            .post(uri)
            .headers(headers)
            .body(write(payload))
            .contentType("application/json")
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
        case _ =>
          Future.successful(Left("Market service not initialized"))
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
          obj.value.get("payload") match {
            case Some(payload: ujson.Arr) =>
              if (payload.arr.length != assets.length) {
                Left(
                  s"Mobula response length ${payload.arr.length} doesn't match request length ${assets.length}"
                )
              } else {
                Try {
                  assets.zip(payload.arr).flatMap { case (asset, value) =>
                    for {
                      price     <- value("priceUSD").numOpt
                      liquidity <- value("liquidityUSD").numOpt
                      result    <- validateMobulaData(asset, price, liquidity)
                    } yield {
                      result
                    }
                  }
                }.toEither.left.map { error =>
                  error.getMessage
                }
              }
            case _ =>
              Left(s"JSON isn't an array: $obj")
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
            ArraySeq
              .from(obj.value.flatMap { case (name, value) =>
                symbolNamesR.get(name).map { ids =>
                  ids.map { id =>
                    CoingeckoPrice(id, value(baseCurrency).num)
                  }
                }
              })
              .flatten
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
        symbol: String,
        decimals: Int
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

  final private[market] case class MobulaPriceRequestAsset(
      address: String,
      blockchain: String
  )

  object MobulaPriceRequestAsset {
    implicit val readWriter: ReadWriter[MobulaPriceRequestAsset] = macroRW
  }

  final private[market] case class MobulaPriceRequest(
      items: ArraySeq[MobulaPriceRequestAsset]
  )

  object MobulaPriceRequest {
    implicit val readWriter: ReadWriter[MobulaPriceRequest] = macroRW
  }

  final private case class CoingeckoPrice(symbol: String, price: Double) extends Price

  final private case class PowfiPrice(symbol: String, quoteSymbol: String, price: Double)
}
