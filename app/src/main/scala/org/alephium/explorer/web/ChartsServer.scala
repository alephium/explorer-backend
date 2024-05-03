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
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile
import sttp.model.StatusCode

import org.alephium.api.ApiError
import org.alephium.api.model.TimeInterval
import org.alephium.explorer.api.ChartsEndpoints
import org.alephium.explorer.api.model.{IntervalType, TimedCount}
import org.alephium.explorer.config.ExplorerConfig
import org.alephium.explorer.service.{HashrateService, TransactionHistoryService}

class ChartsServer(
    maxTimeInterval: ExplorerConfig.MaxTimeInterval,
    services: ExplorerConfig.Services
)(implicit
    val executionContext: ExecutionContext,
    dc: DatabaseConfig[PostgresProfile]
) extends Server
    with ChartsEndpoints {

  val hashrateRoutes: Option[ArraySeq[Router => Route]] =
    Option.when(services.hashrate.enable)(
      ArraySeq(
        route(getHashrates.serverLogic[Future] { case (timeInterval, interval) =>
          validateTimeInterval(timeInterval, interval) {
            HashrateService.get(timeInterval.from, timeInterval.to, interval)
          }
        })
      )
    )

  val txHistoryRoutes: Option[ArraySeq[Router => Route]] =
    Option.when(services.txHistory.enable)(
      ArraySeq(
        route(getAllChainsTxCount.serverLogic[Future] { case (timeInterval, interval) =>
          validateTimeInterval(timeInterval, interval) {
            TransactionHistoryService
              .getAllChains(timeInterval.from, timeInterval.to, interval)
              .map { seq =>
                seq.map { case (timestamp, count) =>
                  TimedCount(timestamp, count)
                }
              }
          }
        }),
        route(getPerChainTxCount.serverLogic[Future] { case (timeInterval, interval) =>
          validateTimeInterval(timeInterval, interval) {
            TransactionHistoryService
              .getPerChain(timeInterval.from, timeInterval.to, interval)
          }
        })
      )
    )

  val routes: ArraySeq[Router => Route] =
    hashrateRoutes.getOrElse(ArraySeq.empty) ++ txHistoryRoutes.getOrElse(ArraySeq.empty)

  private def validateTimeInterval[A](timeInterval: TimeInterval, intervalType: IntervalType)(
      contd: => Future[A]
  ): Future[Either[ApiError[_ <: StatusCode], A]] =
    IntervalType.validateTimeInterval(
      timeInterval,
      intervalType,
      maxTimeInterval.hourly,
      maxTimeInterval.daily,
      maxTimeInterval.weekly
    )(contd)
}
