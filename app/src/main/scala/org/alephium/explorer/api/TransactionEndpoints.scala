// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.api

import sttp.tapir._
import sttp.tapir.generic.auto._

import org.alephium.api.Endpoints.jsonBody
import org.alephium.explorer.api.EndpointExamples._
import org.alephium.explorer.api.model._
import org.alephium.protocol.model.TransactionId

trait TransactionEndpoints extends BaseEndpoint {

  private def transactionsEndpoint =
    baseEndpoint
      .tag("Transactions")
      .in("transactions")

  def getTransactionById: BaseEndpoint[TransactionId, TransactionLike] =
    transactionsEndpoint.get
      .in(path[TransactionId]("transaction_hash"))
      .out(jsonBody[TransactionLike])
      .summary("Get a transaction with hash")
}
