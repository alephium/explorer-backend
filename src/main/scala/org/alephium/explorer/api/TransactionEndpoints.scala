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
import sttp.tapir.json.circe.jsonBody

import org.alephium.explorer.api.Codecs.transactionHashTapirCodec
import org.alephium.explorer.api.Schemas.{blockHashSchema, hashSchema, u256Schema}
import org.alephium.explorer.api.model.Transaction

trait TransactionEndpoints extends BaseEndpoint {

  private val transactionsEndpoint =
    baseEndpoint
      .tag("Transactions")
      .in("transactions")

  val getTransactionById: Endpoint[Transaction.Hash, ApiError, Transaction, Nothing] =
    transactionsEndpoint.get
      .in(path[Transaction.Hash]("transaction-hash"))
      .out(jsonBody[Transaction])
      .description("Get a transaction with hash")
}
