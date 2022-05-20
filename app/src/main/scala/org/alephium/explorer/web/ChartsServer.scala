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

import scala.concurrent.ExecutionContext

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import org.alephium.explorer.api.ChartsEndpoints
import org.alephium.explorer.api.model.TimedValues
import org.alephium.explorer.service.{HashrateService, TransactionHistoryService}
import org.alephium.util.Duration

class ChartsServer(val blockflowFetchMaxAge: Duration)(implicit executionContext: ExecutionContext,
                                                       dc: DatabaseConfig[PostgresProfile])
    extends Server
    with ChartsEndpoints {

  val route: Route =
    toRoute(getHashrates) {
      case (timeInterval, interval) =>
        HashrateService.get(timeInterval.from, timeInterval.to, interval).map(Right(_))
    } ~
      toRoute(getTxCount) {
        case (timeInterval, interval, perChain) =>
          if (perChain) {
            TransactionHistoryService
              .getPerChain(timeInterval.from, timeInterval.to, interval)
              .map(Right(_))
          } else {
            TransactionHistoryService
              .getAllChains(timeInterval.from, timeInterval.to, interval)
              .map { seq =>
                Right(seq.map {
                  case (timestamp, count) => TimedValues(timestamp, count)
                })
              }
          }
      }
}
