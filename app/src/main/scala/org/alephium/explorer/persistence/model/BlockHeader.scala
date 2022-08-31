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

import scala.math.Ordering.Implicits.infixOrderingOps

import akka.util.ByteString

import org.alephium.explorer.Hash
import org.alephium.explorer.api.model._
import org.alephium.util.TimeStamp

final case class BlockHeader(
    hash: BlockEntry.Hash,
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
    parent: Option[BlockEntry.Hash]
) {
  def toApi(deps: Seq[BlockEntry.Hash], transactions: Seq[Transaction]): BlockEntry =
    BlockEntry(hash, timestamp, chainFrom, chainTo, height, deps, transactions, mainChain, hashrate)

  val toLiteApi: BlockEntryLite =
    BlockEntryLite(hash, timestamp, chainFrom, chainTo, height, txsCount, mainChain, hashrate)
}

object BlockHeader {
  def fromEntity(blockEntity: BlockEntity, groupNum: Int): BlockHeader =
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
      blockEntity.parent(groupNum)
    )

  /**
    * Finds a block with maximum height. If two blocks have the same
    * height, returns the one with maximum timestamp.
    * */
  def maxHeight(blocks: Iterable[BlockHeader]): Option[BlockHeader] =
    blocks.foldLeft(Option.empty[BlockHeader]) {
      case (currentMax, header) =>
        currentMax match {
          case Some(current) =>
            if (header.height > current.height) {
              Some(header)
            } else if (header.height == current.height) {
              maxTimeStamp(Array(current, header))
            } else {
              currentMax
            }

          case None =>
            Some(header)
        }
    }

  /** Finds a block with maximum timestamp */
  def maxTimeStamp(blocks: Iterable[BlockHeader]): Option[BlockHeader] =
    blocks.foldLeft(Option.empty[BlockHeader]) {
      case (currentMax, header) =>
        currentMax match {
          case Some(current) =>
            if (header.timestamp > current.timestamp) {
              Some(header)
            } else {
              currentMax
            }

          case None =>
            Some(header)
        }
    }

  /** Filter blocks within the given chain */
  @inline def filterBlocksInChain(blocks: Iterable[BlockHeader],
                                  chainFrom: GroupIndex,
                                  chainTo: GroupIndex): Iterable[BlockHeader] =
    blocks.filter { header =>
      header.chainFrom == chainFrom &&
      header.chainTo == chainTo
    }

  /** Sum height of all blocks */
  def sumHeight(blocks: Iterable[BlockHeader]): Option[Height] =
    blocks.foldLeft(Option.empty[Height]) {
      case (currentSum, header) =>
        currentSum match {
          case Some(sum) => Some(Height.unsafe(sum.value + header.height.value))
          case None      => Some(header.height)
        }
    }
}
