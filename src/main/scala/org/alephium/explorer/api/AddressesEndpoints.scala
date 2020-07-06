package org.alephium.explorer.api

import sttp.tapir._
import sttp.tapir.json.circe.jsonBody

import org.alephium.explorer.api.Codecs._
import org.alephium.explorer.api.Schemas._
import org.alephium.explorer.api.model.{Address, Transaction}

trait AddressesEndpoints extends BaseEndpoint {

  private val addressesEndpoint =
    baseEndpoint
      .tag("Addressess")
      .in("addresses")

  val getTransactionsByAddress: Endpoint[Address, ApiError, Seq[Transaction], Nothing] =
    addressesEndpoint.get
      .in(path[Address]("address"))
      .in("transactions")
      .out(jsonBody[Seq[Transaction]])
      .description("List transactions of a given address")
}
