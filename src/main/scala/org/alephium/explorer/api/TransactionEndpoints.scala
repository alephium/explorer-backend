package org.alephium.explorer.api

import sttp.tapir._
import sttp.tapir.json.circe.jsonBody

import org.alephium.explorer.api.Codecs._
import org.alephium.explorer.api.Schemas._
import org.alephium.explorer.api.model.Transaction

trait TransactionEndpoints extends BaseEndpoint {

  private val transactionsEndpoint =
    baseEndpoint
      .tag("Transactions")
      .in("transactions")

  val getTransactionById: Endpoint[Transaction.Hash, ApiError, Transaction, Nothing] =
    transactionsEndpoint.get
      .in(path[Transaction.Hash]("transaction_hash"))
      .out(jsonBody[Transaction])
      .description("Get a transaction with hash")
}
