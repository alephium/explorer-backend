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
