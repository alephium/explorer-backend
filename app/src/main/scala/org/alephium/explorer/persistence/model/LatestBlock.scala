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

import java.math.BigInteger

import akka.util.ByteString

import org.alephium.explorer.api.model.{BlockEntry, GroupIndex, Height}
import org.alephium.util.TimeStamp

final case class LatestBlock(
    hash: BlockEntry.Hash,
    timestamp: TimeStamp,
    chainFrom: GroupIndex,
    chainTo: GroupIndex,
    height: Height,
    target: ByteString,
    hashrate: BigInteger
)

object LatestBlock {
  def fromEntity(block: BlockEntity): LatestBlock = {
    LatestBlock(
      block.hash,
      block.timestamp,
      block.chainFrom,
      block.chainTo,
      block.height,
      block.target,
      block.hashrate
    )
  }
}
