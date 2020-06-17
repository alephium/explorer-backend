package org.alephium.explorer.api

import sttp.tapir._
import sttp.tapir.json.circe.jsonBody

import org.alephium.explorer.Hash
import org.alephium.explorer.api.Codecs._
import org.alephium.explorer.api.Schemas._
import org.alephium.explorer.api.model.Transaction

trait TransactionEndpoints extends BaseEndoint {

  private val transactionsEndpoint =
    baseEndpoint
      .tag("Transactions")
      .in("transactions")

  val getTransactionById: Endpoint[Hash, ApiError, Transaction, Nothing] =
    transactionsEndpoint.get
      .in(path[Hash]("transactionID"))
      .out(jsonBody[Transaction])
}
