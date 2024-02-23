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

import scala.collection.immutable.ArraySeq

import akka.util.ByteString

import org.alephium.api.UtilJson._
import org.alephium.explorer.api.Codecs._
import org.alephium.explorer.api.Json.groupIndexReadWriter
import org.alephium.explorer.api.model.Height
import org.alephium.json.Json._
import org.alephium.protocol.model.{BlockHash, GroupIndex}
import org.alephium.util.TimeStamp

final case class LatestBlock(
    hash: BlockHash,
    timestamp: TimeStamp,
    chainFrom: GroupIndex,
    chainTo: GroupIndex,
    height: Height,
    deps: Option[ArraySeq[BlockHash]], // currently optional for backward compatibility
    target: ByteString,
    hashRate: BigInteger
)

object LatestBlock {
  implicit val codec: ReadWriter[LatestBlock] = macroRW

  def fromEntity(block: BlockEntity): LatestBlock = {
    LatestBlock(
      block.hash,
      block.timestamp,
      block.chainFrom,
      block.chainTo,
      block.height,
      Some(block.deps),
      block.target,
      block.hashrate
    )
  }
}
