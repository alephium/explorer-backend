// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.model

import java.math.BigInteger

import scala.collection.immutable.ArraySeq

import akka.util.ByteString

import org.alephium.explorer.api.model.{BlockEntry, BlockEntryLite, GhostUncle, Height}
import org.alephium.protocol.Hash
import org.alephium.protocol.model.{BlockHash, GroupIndex, TransactionId}
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
    ghostUncles: Option[ArraySeq[GhostUncle]],
    conflictedTxs: Option[ArraySeq[TransactionId]]
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
      ghostUncles.getOrElse(ArraySeq.empty),
      conflictedTxs
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
      ghostUncles,
      blockEntity.conflictedTxs
    )
  }
}
