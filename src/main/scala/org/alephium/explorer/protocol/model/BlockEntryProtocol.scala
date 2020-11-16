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

package org.alephium.explorer.protocol.model

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

import org.alephium.api.CirceUtils.{avectorCodec, timestampCodec}
import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence.model._
import org.alephium.util.{AVector, TimeStamp}

final case class BlockEntryProtocol(
    hash: BlockEntry.Hash,
    timestamp: TimeStamp,
    chainFrom: GroupIndex,
    chainTo: GroupIndex,
    height: Height,
    deps: AVector[BlockEntry.Hash],
    transactions: AVector[TransactionProtocol]
) {
  lazy val toEntity: BlockEntity =
    BlockEntity(
      hash,
      timestamp,
      chainFrom,
      chainTo,
      height,
      deps,
      transactions.map(_.toEntity(hash, timestamp)),
      transactions.flatMap(tx => tx.inputs.map(_.toEntity(tx.hash))),
      transactions.flatMap(tx =>
        tx.outputs.mapWithIndex { case (out, index) => out.toEntity(tx.hash, index, timestamp) }),
      mainChain = false
    )
}

object BlockEntryProtocol {
  implicit val codec: Codec[BlockEntryProtocol] = deriveCodec[BlockEntryProtocol]
}
