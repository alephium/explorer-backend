// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.api.model

import java.math.BigInteger

import scala.collection.immutable.ArraySeq

import akka.util.ByteString

import org.alephium.api.UtilJson._
import org.alephium.explorer.api.Codecs._
import org.alephium.explorer.api.Json.groupIndexReadWriter
import org.alephium.explorer.service.FlowEntity
import org.alephium.json.Json._
import org.alephium.protocol.Hash
import org.alephium.protocol.model.{BlockHash, GroupIndex}
import org.alephium.util.{AVector, TimeStamp}

final case class BlockEntry(
    hash: BlockHash,
    timestamp: TimeStamp,
    chainFrom: GroupIndex,
    chainTo: GroupIndex,
    height: Height,
    deps: ArraySeq[BlockHash],
    nonce: ByteString,
    version: Byte,
    depStateHash: Hash,
    txsHash: Hash,
    txNumber: Int,
    target: ByteString,
    hashRate: BigInteger,
    parent: Option[BlockHash],
    mainChain: Boolean,
    ghostUncles: ArraySeq[GhostUncle]
) extends FlowEntity {

  def toProtocol(
      transactions: ArraySeq[Transaction]
  ): org.alephium.api.model.BlockEntry = {
    org.alephium.api.model.BlockEntry(
      hash,
      timestamp,
      chainFrom.value,
      chainTo.value,
      height.value,
      AVector.from(deps),
      AVector.from(transactions.map(_.toProtocol())),
      nonce,
      version,
      depStateHash,
      txsHash,
      target,
      AVector.from(ghostUncles.map(_.toProtocol()))
    )
  }

}

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
