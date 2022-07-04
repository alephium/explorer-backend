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

package org.alephium.explorer.persistence.model

import org.alephium.explorer.Hash
import org.alephium.explorer.api.model.{Address, BlockEntry, Input, OutputRef, Transaction}
import org.alephium.protocol
import org.alephium.protocol.vm.UnlockScript
import org.alephium.serde.deserialize
import org.alephium.util.{Hex, TimeStamp}

final case class InputEntity(
    blockHash: BlockEntry.Hash,
    txHash: Transaction.Hash,
    timestamp: TimeStamp,
    hint: Int,
    outputRefKey: Hash,
    unlockScript: Option[String],
    mainChain: Boolean,
    inputOrder: Int,
    txOrder: Int
) {
  def toApi(outputRef: OutputEntity): Input =
    Input(
      OutputRef(hint, outputRefKey),
      unlockScript,
      outputRef.txHash,
      outputRef.address,
      outputRef.amount,
      outputRef.tokens
    )

  lazy val address: Option[Address] = {
    unlockScript.flatMap { unlockScript =>
      Hex.from(unlockScript).flatMap(us => deserialize[UnlockScript](us).toOption).flatMap {
        case UnlockScript.P2PKH(pubKey) =>
          Some(Address.unsafe(protocol.model.Address.p2pkh(pubKey).toBase58))
        case _ => None
      }
    }
  }
}
