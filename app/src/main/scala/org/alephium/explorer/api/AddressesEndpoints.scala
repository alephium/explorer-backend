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
import org.alephium.explorer.api.Codecs
import org.alephium.explorer.api.model._
import org.alephium.protocol.Hash

// scalastyle:off magic.number
trait AddressesEndpoints extends BaseEndpoint with QueryParams {

  private val addressesEndpoint =
    baseEndpoint
      .tag("Addresses")
      .in("addresses")

  private val addressesTokensEndpoint =
    baseEndpoint
      .tag("Addresses")
      .in("addresses")
      .in(path[Address]("address")(Codecs.addressTapirCodec))
      .in("tokens")

  val getAddressInfo: BaseEndpoint[Address, AddressInfo] =
    addressesEndpoint.get
      .in(path[Address]("address")(Codecs.addressTapirCodec))
      .out(jsonBody[AddressInfo])
      .description("Get address information")

  val getTransactionsByAddressDEPRECATED: BaseEndpoint[(Address, Pagination), Seq[Transaction]] =
    addressesEndpoint.get
      .in(path[Address]("address")(Codecs.addressTapirCodec))
      .in("transactions-DEPRECATED")
      .in(pagination)
      .out(jsonBody[Seq[Transaction]])
      .description("List transactions of a given address")

  val getTransactionsByAddress: BaseEndpoint[(Address, Pagination), Seq[Transaction]] =
    addressesEndpoint.get
      .in(path[Address]("address")(Codecs.addressTapirCodec))
      .in("transactions")
      .in(pagination)
      .out(jsonBody[Seq[Transaction]])
      .description("List transactions of a given address")

  val getTotalTransactionsByAddress: BaseEndpoint[Address, Int] =
    addressesEndpoint.get
      .in(path[Address]("address")(Codecs.addressTapirCodec))
      .in("total-transactions")
      .out(jsonBody[Int])
      .description("Get total transactions of a given address")

  val getAddressBalance: BaseEndpoint[Address, AddressBalance] =
    addressesEndpoint.get
      .in(path[Address]("address")(Codecs.addressTapirCodec))
      .in("balance")
      .out(jsonBody[AddressBalance])
      .description("Get address balance")

  val listAddressTokens: BaseEndpoint[Address, Seq[Hash]] =
    addressesTokensEndpoint.get
      .out(jsonBody[Seq[Hash]])
      .description("List address tokens")

  val getAddressTokenBalance: BaseEndpoint[(Address, Hash), AddressBalance] =
    addressesTokensEndpoint.get
      .in(path[Hash]("token-id"))
      .in("balance")
      .out(jsonBody[AddressBalance])
      .description("Get address balance of given token")

  val listAddressTokenTransactions: BaseEndpoint[(Address, Hash, Pagination), Seq[Transaction]] =
    addressesTokensEndpoint.get
      .in(path[Hash]("token-id"))
      .in("transactions")
      .in(pagination)
      .out(jsonBody[Seq[Transaction]])
      .description("List address tokens")

  val isAddressActive: BaseEndpoint[Address, Boolean] =
    addressesEndpoint.get
      .in(path[Address]("address")(Codecs.addressTapirCodec))
      .in("active")
      .out(jsonBody[Boolean])
      .description("Is the address active (at least 1 tx)")
}
