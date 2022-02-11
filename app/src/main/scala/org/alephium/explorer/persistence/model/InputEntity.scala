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
import org.alephium.explorer.api.model.{Address, BlockEntry, Input, Output, Transaction}
import org.alephium.util.{TimeStamp, U256}

final case class InputEntity(
    blockHash: BlockEntry.Hash,
    txHash: Transaction.Hash,
    timestamp: TimeStamp,
    hint: Int,
    outputRefKey: Hash,
    unlockScript: Option[String],
    mainChain: Boolean,
    order: Int,
    address: Option[Address],
    outputTxHash: Option[Transaction.Hash],
    outputAmount: Option[U256]
) {
  def toApi(outputRef: OutputEntity): Input =
    Input(
      Output.Ref(hint, outputRefKey),
      unlockScript,
      outputRef.txHash,
      outputRef.address,
      outputRef.amount
    )
}
