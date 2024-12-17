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

import org.alephium.explorer.api.EventsEndpoints
import org.alephium.explorer.config.ExplorerConfig
import org.alephium.explorer.persistence.DBRunner._
import org.alephium.explorer.persistence.queries.EventQueries._

class EventServer(implicit
    val executionContext: ExecutionContext,
    val serverConfig: ExplorerConfig.Server,
    dc: DatabaseConfig[PostgresProfile]
) extends Server
    with EventsEndpoints {
  val routes: ArraySeq[Router => Route] =
    ArraySeq(
      route(getEventsByTxId.serverLogicSuccess[Future] { txId =>
        run(getEventsByTxIdQuery(txId)).map(_.map(_.toApi))
      }),
      route(getEventsByContractAddress.serverLogicSuccess[Future] { case (address, pagination) =>
        run(getEventsByContractAddressQuery(address, pagination)).map(_.map(_.toApi))
      }),
      route(getEventsByContractAndInputAddress.serverLogicSuccess[Future] {
        case (contract, input, pagination) =>
          run(getEventsByContractAndInputAddressQuery(contract, input, pagination))
            .map(_.map(_.toApi))
      })
    )
}
