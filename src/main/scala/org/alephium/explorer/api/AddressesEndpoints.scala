package org.alephium.explorer.api

import sttp.tapir._
import sttp.tapir.json.circe.jsonBody

import org.alephium.explorer.Hash
import org.alephium.explorer.api.Codecs._
import org.alephium.explorer.api.Schemas._
import org.alephium.explorer.api.model.Transaction

trait AddressesEndpoints extends BaseEndoint {

  private val addressesEndpoint =
    baseEndpoint
      .tag("Addressess")
      .in("addresses")

  val getTransactionsByAddress: Endpoint[Hash, ApiError, Seq[Transaction], Nothing] =
    addressesEndpoint.get
      .in(path[Hash]("address"))
      .in("transactions")
      .out(jsonBody[Seq[Transaction]])
}
