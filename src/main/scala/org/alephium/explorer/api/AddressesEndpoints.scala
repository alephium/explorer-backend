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

import org.alephium.api.{alfJsonBody => jsonBody, BaseEndpoint}
import org.alephium.explorer.api.Codecs
import org.alephium.explorer.api.model.{Address, AddressInfo, Pagination, Transaction}

// scalastyle:off magic.number
trait AddressesEndpoints extends BaseEndpoint with QueryParams {

  private val addressesEndpoint =
    baseEndpoint
      .tag("Addressess")
      .in("addresses")

  val getAddressInfo: BaseEndpoint[Address, AddressInfo] =
    addressesEndpoint.get
      .in(path[Address]("address")(Codecs.addressTapirCodec))
      .out(jsonBody[AddressInfo])
      .description("Get address informations")

  val getTransactionsByAddress: BaseEndpoint[(Address, Pagination), Seq[Transaction]] =
    addressesEndpoint.get
      .in(path[Address]("address")(Codecs.addressTapirCodec))
      .in("transactions")
      .in(pagination)
      .out(jsonBody[Seq[Transaction]])
      .description("List transactions of a given address")
}
