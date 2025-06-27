// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.service

import scala.collection.immutable.ArraySeq

import akka.util.ByteString

import org.alephium.explorer.AnyOps
import org.alephium.explorer.api.model.{GhostUncle, Height}
import org.alephium.protocol.Hash
import org.alephium.protocol.model.{BlockHash, GroupIndex}
import org.alephium.util.TimeStamp

@SuppressWarnings(Array("org.wartremover.warts.SeqApply"))
trait FlowEntity {
  def hash: BlockHash
  def timestamp: TimeStamp
  def chainFrom: GroupIndex
  def chainTo: GroupIndex
  def height: Height
  def deps: ArraySeq[BlockHash]
  def nonce: ByteString
  def version: Byte
  def depStateHash: Hash
  def txsHash: Hash
  def target: ByteString
  def ghostUncles: ArraySeq[GhostUncle]
  def mainChain: Boolean

  def parent(groupNum: Int): Option[BlockHash] =
    if (isGenesis) {
      None
    } else {
      Some(deps.takeRight(groupNum).apply(chainTo.value))
    }

  def isGenesis: Boolean = height === Height.genesis
}
