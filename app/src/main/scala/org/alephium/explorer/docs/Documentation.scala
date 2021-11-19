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

package org.alephium.explorer.docs

import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
import sttp.tapir.openapi.OpenAPI

import org.alephium.explorer.api._

trait Documentation
    extends BlockEndpoints
    with TransactionEndpoints
    with AddressesEndpoints
    with InfosEndpoints
    with OpenAPIDocsInterpreter {
  val docs: OpenAPI = toOpenAPI(
    List(
      listBlocks,
      getBlockByHash,
      getBlockTransactions,
      getTransactionById,
      getAddressInfo,
      getTransactionsByAddress,
      getInfos,
      getTokenSupply,
      listTokenSupply
    ),
    "Alephium Explorer API",
    "1.0"
  )
}
