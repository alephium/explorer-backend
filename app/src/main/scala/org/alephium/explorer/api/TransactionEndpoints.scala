// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.api

import scala.collection.immutable.ArraySeq

import sttp.tapir._
import sttp.tapir.generic.auto._

import org.alephium.api.Endpoints.jsonBody
import org.alephium.api.model.DecodeUnsignedTx
import org.alephium.explorer.api.EndpointExamples._
import org.alephium.explorer.api.model._
import org.alephium.protocol.model.TransactionId

trait TransactionEndpoints extends BaseEndpoint with QueryParams {

  private val transactionsEndpoint =
    baseEndpoint
      .tag("Transactions")
      .in("transactions")

  val getTransactionById: BaseEndpoint[TransactionId, TransactionLike] =
    transactionsEndpoint.get
      .in(path[TransactionId]("transaction_hash"))
      .out(jsonBody[TransactionLike])
      .summary("Get a transaction with hash")

  val decodeUnsignedTx: BaseEndpoint[DecodeUnsignedTx, DecodeUnsignedTxResult] =
    transactionsEndpoint.post
      .in("decode-unsigned-tx")
      .in(jsonBody[DecodeUnsignedTx])
      .out(jsonBody[DecodeUnsignedTxResult])
      .summary("Decode an unsigned transaction")

  val listTransactions: BaseEndpoint[(Option[TxStatusType], Pagination), ArraySeq[Transaction]] =
    transactionsEndpoint.get
      .in(optionalTxStatusTypeQuery)
      .in(pagination)
      .out(jsonBody[ArraySeq[Transaction]])
      .summary("List transactions")
}
