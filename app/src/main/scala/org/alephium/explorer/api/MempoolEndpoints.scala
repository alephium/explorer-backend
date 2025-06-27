// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.api

import scala.collection.immutable.ArraySeq

import sttp.tapir._
import sttp.tapir.generic.auto._

import org.alephium.api.Endpoints.jsonBody
import org.alephium.explorer.api.EndpointExamples._
import org.alephium.explorer.api.model.{MempoolTransaction, Pagination}

trait MempoolEndpoints extends BaseEndpoint with QueryParams {

  private val mempoolEndpoint =
    baseEndpoint
      .tag("Mempool")
      .in("mempool")

  val listMempoolTransactions: BaseEndpoint[Pagination, ArraySeq[MempoolTransaction]] =
    mempoolEndpoint.get
      .in("transactions")
      .in(pagination)
      .out(jsonBody[ArraySeq[MempoolTransaction]])
      .summary("list mempool transactions")
}
