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
