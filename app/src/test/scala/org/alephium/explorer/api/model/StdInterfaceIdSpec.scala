// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.api.model

import org.scalacheck.Gen

import org.alephium.explorer.AlephiumSpec
import org.alephium.explorer.GenApiModel.stdInterfaceIdGen
import org.alephium.explorer.GenCoreProtocol.hashGen

class StdInterfaceIdSpec() extends AlephiumSpec {

  "StdInterfaceId" should {
    "validate" in {
      forAll(stdInterfaceIdGen) { interfaceId =>
        StdInterfaceId.validate(interfaceId.value) is Some(interfaceId)
      }
    }
    "validate unknown interface" in {
      forAll(Gen.choose(2, 8), hashGen) { case (size, hash) =>
        // * 2 two have bytes
        val hex      = hash.toHexString
        val id       = hex.take(2 * size)
        val category = id.take(4)
        val result   = StdInterfaceId.validate(id).get

        result is StdInterfaceId.Unknown(id)
        result.category is category

        StdInterfaceId.validate(hex.take(2 * size + 1)) is None
        StdInterfaceId.validate(hex.take(2 * size - 1)) is None
        StdInterfaceId.validate(hex.take(2)) is None
        StdInterfaceId.validate(hex.take(18)) is None
      }
    }

    "validate empty string as Non-Standard" in {
      StdInterfaceId.validate("") is Some(StdInterfaceId.NonStandard)
    }
  }
}
