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

import org.alephium.explorer.api.model.Height
import org.alephium.explorer.service.FlowEntity
import org.alephium.protocol.Hash
import org.alephium.protocol.model.{BlockHash, GroupIndex}
import org.alephium.util.TimeStamp

final case class BlockEntity(
    hash: BlockHash,
    timestamp: TimeStamp,
    chainFrom: GroupIndex,
    chainTo: GroupIndex,
    height: Height,
    deps: ArraySeq[BlockHash],
    transactions: ArraySeq[TransactionEntity],
    inputs: ArraySeq[InputEntity],
    outputs: ArraySeq[OutputEntity],
    mainChain: Boolean,
    nonce: ByteString,
    version: Byte,
    depStateHash: Hash,
    txsHash: Hash,
    target: ByteString,
    hashrate: BigInteger
) extends FlowEntity {
  def updateMainChain(newMainChain: Boolean): BlockEntity = {
    this.copy(
      mainChain = newMainChain,
      transactions = transactions.map(_.copy(mainChain = newMainChain)),
      inputs = inputs.map(_.copy(mainChain = newMainChain)),
      outputs = outputs.map(_.copy(mainChain = newMainChain))
    )
  }

  /** Builds entries for block_deps table */
  def toBlockDepEntities(): ArraySeq[BlockDepEntity] =
    deps.zipWithIndex map { case (dep, i) =>
      BlockDepEntity(hash = hash, dep = dep, order = i)
    }

  @inline def toBlockHeader(groupNum: Int): BlockHeader =
    BlockHeader.fromEntity(this, groupNum)

}
