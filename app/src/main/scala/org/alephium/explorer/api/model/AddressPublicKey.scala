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

import akka.util.ByteString

import org.alephium.api.UtilJson._
import org.alephium.api.model.BuildTxCommon._
import org.alephium.explorer.api.Json._
import org.alephium.json.Json._
import org.alephium.protocol.vm.PublicKeyLike

final case class AddressPublicKey(publicKey: ByteString, publicKeyType: PublicKeyType)

object AddressPublicKey {
  implicit val readWriter: ReadWriter[AddressPublicKey] = macroRW

  def from(publicKeyLike: PublicKeyLike): AddressPublicKey = {
    AddressPublicKey(
      publicKeyLike.publicKey.bytes,
      getPublicKeyType(publicKeyLike)
    )
  }
  def getPublicKeyType(publicKeyLike: PublicKeyLike): PublicKeyType = {
    publicKeyLike match {
      case PublicKeyLike.SecP256K1(_) => GLSecP256K1
      case PublicKeyLike.SecP256R1(_) => GLSecP256R1
      case PublicKeyLike.ED25519(_)   => GLED25519
      case PublicKeyLike.WebAuthn(_)  => GLWebAuthn
    }
  }
}
