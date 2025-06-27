// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.web

import scala.collection.immutable.{ArraySeq, ListMap}
import scala.concurrent.{ExecutionContext, Future}

import io.vertx.ext.web._

import org.alephium.api.ApiError
import org.alephium.explorer.api.MarketEndpoints
import org.alephium.explorer.service.market.MarketService

class MarketServer(
    marketService: MarketService
)(implicit
    val executionContext: ExecutionContext
) extends Server
    with MarketEndpoints {

  val routes: ArraySeq[Router => Route] =
    ArraySeq(
      route(getPrices.serverLogicPure[Future] { case (currency, ids) =>
        for {
          _ <- MarketServer.validateCurrency(currency, marketService.currencies)
          result <- marketService
            .getPrices(ids, currency)
            .left
            .map { error =>
              ApiError.ServiceUnavailable(error)
            }
        } yield result
      }),
      route(getPriceChart.serverLogicPure[Future] { case (id, currency) =>
        for {
          _ <- MarketServer.validateSymbol(id, marketService.chartSymbolNames)
          _ <- MarketServer.validateCurrency(currency, marketService.currencies)
          result <- marketService
            .getPriceChart(id, currency)
            .left
            .map { error =>
              ApiError.ServiceUnavailable(error)
            }
        } yield result
      })
    )
}

object MarketServer {
  def validateSymbol(
      symbol: String,
      symbolName: ListMap[String, String]
  ): Either[ApiError.NotFound, Unit] = {
    if (symbolName.keys.exists(_ == symbol)) {
      Right(())
    } else {
      Left(ApiError.NotFound(symbol))
    }
  }

  def validateCurrency(
      currency: String,
      currencies: ArraySeq[String]
  ): Either[ApiError.NotFound, Unit] = {
    if (currencies.exists(_ == currency)) {
      Right(())
    } else {
      Left(ApiError.NotFound(currency))
    }
  }
}
