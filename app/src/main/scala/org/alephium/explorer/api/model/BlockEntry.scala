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

import java.math.BigInteger

import scala.collection.immutable.ArraySeq

import org.alephium.api.UtilJson._
import org.alephium.explorer.api.Codecs._
import org.alephium.explorer.service.FlowEntity
import org.alephium.json.Json._
import org.alephium.protocol.model.BlockHash
import org.alephium.util.TimeStamp

final case class BlockEntry(
    hash: BlockHash,
    timestamp: TimeStamp,
    chainFrom: GroupIndex,
    chainTo: GroupIndex,
    height: Height,
    deps: ArraySeq[BlockHash],
    transactions: ArraySeq[Transaction],
    mainChain: Boolean,
    hashRate: BigInteger
) extends FlowEntity

object BlockEntry {
  implicit val codec: ReadWriter[BlockEntry] = macroRW
}

final case class BlockEntryLite(
    hash: BlockHash,
    timestamp: TimeStamp,
    chainFrom: GroupIndex,
    chainTo: GroupIndex,
    height: Height,
    txNumber: Int,
    mainChain: Boolean,
    hashRate: BigInteger
)
object BlockEntryLite {
  implicit val codec: ReadWriter[BlockEntryLite] = macroRW
}
