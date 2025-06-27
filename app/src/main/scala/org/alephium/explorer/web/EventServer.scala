// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.web

import scala.collection.immutable.ArraySeq
import scala.concurrent.{ExecutionContext, Future}

import io.vertx.ext.web._
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import org.alephium.explorer.api.EventsEndpoints
import org.alephium.explorer.persistence.DBRunner._
import org.alephium.explorer.persistence.queries.EventQueries._

class EventServer(implicit
    val executionContext: ExecutionContext,
    dc: DatabaseConfig[PostgresProfile]
) extends Server
    with EventsEndpoints {
  val routes: ArraySeq[Router => Route] =
    ArraySeq(
      route(getEventsByTxId.serverLogicSuccess[Future] { txId =>
        run(getEventsByTxIdQuery(txId)).map(_.map(_.toApi))
      }),
      route(getEventsByContractAddress.serverLogicSuccess[Future] {
        case (address, eventIndex, pagination) =>
          run(getEventsByContractAddressQuery(address, eventIndex, pagination)).map(_.map(_.toApi))
      }),
      route(getEventsByContractAndInputAddress.serverLogicSuccess[Future] {
        case (contract, input, eventIndex, pagination) =>
          run(getEventsByContractAndInputAddressQuery(contract, input, eventIndex, pagination))
            .map(_.map(_.toApi))
      })
    )
}
