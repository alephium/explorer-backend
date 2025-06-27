// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.web

import scala.collection.immutable.ArraySeq
import scala.concurrent.{ExecutionContext, Future}

import io.vertx.ext.web._
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import org.alephium.explorer.api.MempoolEndpoints
import org.alephium.explorer.service.TransactionService

class MempoolServer(implicit
    val executionContext: ExecutionContext,
    dc: DatabaseConfig[PostgresProfile]
) extends Server
    with MempoolEndpoints {
  val routes: ArraySeq[Router => Route] = ArraySeq(
    route(listMempoolTransactions.serverLogicSuccess[Future] { pagination =>
      TransactionService
        .listMempoolTransactions(pagination)
    })
  )
}
