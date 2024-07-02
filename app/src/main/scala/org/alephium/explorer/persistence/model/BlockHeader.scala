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

import org.alephium.explorer.api.model.{BlockEntry, BlockEntryLite, GhostUncle, Height}
import org.alephium.protocol.Hash
import org.alephium.protocol.model.{BlockHash, GroupIndex}
import org.alephium.util.TimeStamp

final case class BlockHeader(
    hash: BlockHash,
    timestamp: TimeStamp,
    chainFrom: GroupIndex,
    chainTo: GroupIndex,
    height: Height,
    mainChain: Boolean,
    nonce: ByteString,
    version: Byte,
    depStateHash: Hash,
    txsHash: Hash,
    txsCount: Int,
    target: ByteString,
    hashrate: BigInteger,
    parent: Option[BlockHash],
    deps: ArraySeq[BlockHash],
    ghostUncles: Option[ArraySeq[GhostUncle]]
) {

  def toApi(): BlockEntry =
    BlockEntry(
      hash,
      timestamp,
      chainFrom,
      chainTo,
      height,
      deps,
      nonce,
      version,
      depStateHash,
      txsHash,
      txsCount,
      target,
      hashrate,
      parent,
      mainChain,
      ghostUncles.getOrElse(ArraySeq.empty)
    )

  val toLiteApi: BlockEntryLite =
    BlockEntryLite(
      hash,
      timestamp,
      chainFrom,
      chainTo,
      height,
      txsCount,
      mainChain,
      hashrate
    )
}

object BlockHeader {
  def fromEntity(blockEntity: BlockEntity, groupNum: Int): BlockHeader = {
    val ghostUncles = if (blockEntity.ghostUncles.isEmpty) None else Some(blockEntity.ghostUncles)
    BlockHeader(
      blockEntity.hash,
      blockEntity.timestamp,
      blockEntity.chainFrom,
      blockEntity.chainTo,
      blockEntity.height,
      blockEntity.mainChain,
      blockEntity.nonce,
      blockEntity.version,
      blockEntity.depStateHash,
      blockEntity.txsHash,
      blockEntity.transactions.size,
      blockEntity.target,
      blockEntity.hashrate,
      blockEntity.parent(groupNum),
      blockEntity.deps,
      ghostUncles
    )
  }
}
