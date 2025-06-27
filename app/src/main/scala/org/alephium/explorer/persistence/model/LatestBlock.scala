// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.model

import java.math.BigInteger

import akka.util.ByteString

import org.alephium.explorer.api.model.Height
import org.alephium.protocol.model.{BlockHash, GroupIndex}
import org.alephium.util.TimeStamp

final case class LatestBlock(
    hash: BlockHash,
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

  def empty(): LatestBlock = {
    LatestBlock(
      BlockHash.zero,
      TimeStamp.zero,
      new GroupIndex(-1),
      new GroupIndex(-1),
      Height.unsafe(-1),
      ByteString.empty,
      BigInteger.ZERO
    )
  }
}
