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

import scala.collection.immutable.ArraySeq

import sttp.tapir._
import sttp.tapir.generic.auto._

import org.alephium.api.{alphJsonBody => jsonBody}
import org.alephium.explorer.api.BaseEndpoint
import org.alephium.explorer.api.model.{Pagination, TransactionLike}

trait UnconfirmedTransactionEndpoints extends BaseEndpoint with QueryParams {

  private val unconfirmedTransactionsEndpoint =
    baseEndpoint
      .tag("Transactions")
      .in("unconfirmed-transactions")

  val listUnconfirmedTransactions: BaseEndpoint[Pagination, ArraySeq[TransactionLike]] =
    unconfirmedTransactionsEndpoint.get
      .in(pagination)
      .out(jsonBody[ArraySeq[TransactionLike]])
      .description("list unconfirmed transactions")
}
