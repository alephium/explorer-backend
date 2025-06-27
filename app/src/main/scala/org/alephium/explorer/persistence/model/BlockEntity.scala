// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.model

import java.math.BigInteger

import scala.collection.immutable.ArraySeq

import akka.util.ByteString

import org.alephium.explorer.api.model.{GhostUncle, Height}
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
    hashrate: BigInteger,
    ghostUncles: ArraySeq[GhostUncle]
) extends FlowEntity {

  @inline def toBlockHeader(groupNum: Int): BlockHeader =
    BlockHeader.fromEntity(this, groupNum)

}
