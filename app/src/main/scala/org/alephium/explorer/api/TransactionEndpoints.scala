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

package org.alephium.explorer.api

import sttp.tapir._
import sttp.tapir.generic.auto._

import org.alephium.api.{alphJsonBody => jsonBody}
import org.alephium.explorer.Hash
import org.alephium.explorer.api.model.{Transaction, TransactionLike}
import org.alephium.protocol.model.TransactionId

object TransactionEndpoints extends BaseEndpoint {

  private def transactionsEndpoint =
    baseEndpoint
      .tag("Transactions")
      .in("transactions")

  def getTransactionById: BaseEndpoint[TransactionId, TransactionLike] =
    transactionsEndpoint.get
      .in(path[TransactionId]("transaction_hash"))
      .out(jsonBody[TransactionLike])
      .description("Get a transaction with hash")

  def getOutputRefTransaction: BaseEndpoint[Hash, Transaction] =
    baseEndpoint
      .tag("Transactions")
      .in("transactions")
      .in("by")
      .in("output-ref-key")
      .get
      .in(path[Hash]("output_ref_key"))
      .out(jsonBody[Transaction])
      .description("Get a transaction from a output reference key")
}
