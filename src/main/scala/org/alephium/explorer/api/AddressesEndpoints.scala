package org.alephium.explorer.api

import sttp.tapir._
import sttp.tapir.json.circe.jsonBody

import org.alephium.explorer.api.Codecs._
import org.alephium.explorer.api.Schemas._
import org.alephium.explorer.api.model.{Address, AddressInfo, Transaction}

trait AddressesEndpoints extends BaseEndpoint {

  private val addressesEndpoint =
    baseEndpoint
      .tag("Addressess")
      .in("addresses")

  val getAddressInfo: Endpoint[Address, ApiError, AddressInfo, Nothing] =
    addressesEndpoint.get
      .in(path[Address]("address"))
      .out(jsonBody[AddressInfo])
      .description("Get address informations")

  val getTransactionsByAddress: Endpoint[Address, ApiError, Seq[Transaction], Nothing] =
    addressesEndpoint.get
      .in(path[Address]("address"))
      .in("transactions")
      .out(jsonBody[Seq[Transaction]])
      .description("List transactions of a given address")
}
