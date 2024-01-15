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

import scala.collection.immutable.ArraySeq
import scala.concurrent.{ExecutionContext, Future}

import io.vertx.ext.web._

import org.alephium.api.ApiError
import org.alephium.explorer.api.MarketEndpoints
import org.alephium.explorer.service.MarketService

class MarketServer(
    marketService: MarketService
)(implicit
    val executionContext: ExecutionContext
) extends Server
    with MarketEndpoints {

  val routes: ArraySeq[Router => Route] =
    ArraySeq(
      route(getPrices.serverLogic[Future] { case (currency, ids) =>
        marketService
          .getPrices(ids, currency)
          .map(_.left.map { error =>
            ApiError.ServiceUnavailable(error)
          })
      }),
      route(getExchangeRates.serverLogic[Future] { _ =>
        marketService
          .getExchangeRates()
          .map(_.left.map { error =>
            ApiError.ServiceUnavailable(error)
          })
      }),
      route(getPriceChart.serverLogic[Future] { case (id, currency) =>
        marketService
          .getPriceChart(id, currency)
          .map(_.left.map { error =>
            ApiError.ServiceUnavailable(error)
          })
      }),
      route(getTokenSymbols.serverLogicSuccess[Future] { _ =>
        marketService.getTokenSymbols()
      })
    )
}
