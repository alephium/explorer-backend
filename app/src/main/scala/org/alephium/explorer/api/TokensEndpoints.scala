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
import org.alephium.explorer.api.BaseEndpoint
import org.alephium.explorer.api.model._
import org.alephium.protocol.Hash

// scalastyle:off magic.number
trait TokensEndpoints extends BaseEndpoint with QueryParams {

  private val tokensEndpoint =
    baseEndpoint
      .tag("Tokens")
      .in("tokens")

  val listTokens: BaseEndpoint[Pagination, Seq[Hash]] =
    tokensEndpoint.get
      .in(pagination)
      .out(jsonBody[Seq[Hash]])
      .description("List tokens")

  val listTokenTransactions: BaseEndpoint[(Hash, Pagination), Seq[Transaction]] =
    tokensEndpoint.get
      .in(path[Hash]("token-id"))
      .in("transactions")
      .in(pagination)
      .out(jsonBody[Seq[Transaction]])
      .description("List token transactions")

  val listTokenAddresses: BaseEndpoint[(Hash, Pagination), Seq[Address]] =
    tokensEndpoint.get
      .in(path[Hash]("token-id"))
      .in("addresses")
      .in(pagination)
      .out(jsonBody[Seq[Address]])
      .description("List token addresses")
}
