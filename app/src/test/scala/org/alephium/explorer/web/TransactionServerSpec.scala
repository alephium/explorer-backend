// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.web

import org.alephium.api.ApiError
import org.alephium.explorer._
import org.alephium.explorer.HttpFixture._
import org.alephium.explorer.persistence.DatabaseFixtureForAll
import org.alephium.protocol.ALPH
import org.alephium.protocol.model.UnsignedTransaction
import org.alephium.serde._
import org.alephium.util.{AVector, Hex}

class TransactionServerSpec()
    extends AlephiumFutureSpec
    with DatabaseFixtureForAll
    with HttpServerFixture {

  val server = new TransactionServer()
  val routes = server.routes
  val rawUtxHex = {
    val raw =
      "00040080004e20c1174876e8000137a44447cfff0c6c3951889e73ae1a3b0b1b2bb284cfb01e77cd910bf17cda8f3dcee82b000381818e63bd9e35a5489b52a430accefc608fd60aa2c7c0d1b393b5239aedf6b003c41bc16d674ec8000000622990ad7be0a3d163562c10fd7985ef40a3e41857e7a1583406a785efc9273a00000000000000000000c429a2241af62c00000000dd2354976f12629cfbc141e16f3592927f06c78bdc27d3f7a429602cc27d1200000000000000000000c6d3b40169eefd092ce00000bee85f379545a2ed9f6cceb331288842f378cf0f04012ad4ac8824aae7d6f80a00000000000000000000"
    val protocolUnsignedTx = deserialize[UnsignedTransaction](Hex.unsafe(raw)).toOption.get
    val oversizedInputs    = AVector.fill(ALPH.MaxTxInputNum + 1)(protocolUnsignedTx.inputs.head)
    Hex.toHexString(serialize(protocolUnsignedTx.copy(inputs = oversizedInputs)))
  }

  "reject unsigned transactions with too many inputs" in {
    Post(s"/transactions/decode-unsigned-tx", s"""{"unsignedTx":"$rawUtxHex"}""") check {
      response =>
        response.as[ApiError.BadRequest] is ApiError.BadRequest(
          s"Too many inputs in unsigned transaction, max ${ALPH.MaxTxInputNum}"
        )
    }
  }
}
