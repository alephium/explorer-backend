// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.api.model

import java.math.BigInteger

import scala.collection.immutable.ArraySeq

import akka.util.ByteString

import org.alephium.explorer.service.FlowEntity
import org.alephium.protocol.Hash
import org.alephium.protocol.model.{BlockHash, GroupIndex}
import org.alephium.util.TimeStamp

final case class BlockEntryTest(
    hash: BlockHash,
    timestamp: TimeStamp,
    chainFrom: GroupIndex,
    chainTo: GroupIndex,
    height: Height,
    transactions: ArraySeq[Transaction],
    deps: ArraySeq[BlockHash],
    nonce: ByteString,
    version: Byte,
    depStateHash: Hash,
    txsHash: Hash,
    txsCount: Int,
    target: ByteString,
    hashRate: BigInteger,
    parent: Option[BlockHash],
    mainChain: Boolean,
    ghostUncles: ArraySeq[GhostUncle]
) extends FlowEntity
