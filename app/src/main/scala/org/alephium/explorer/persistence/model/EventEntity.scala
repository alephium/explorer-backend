// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.model

import scala.collection.immutable.ArraySeq

import org.alephium.api.model.Val
import org.alephium.explorer.api.model.Event
import org.alephium.protocol.model.{Address, BlockHash, TransactionId}
import org.alephium.util.TimeStamp

@SuppressWarnings(
  Array("org.wartremover.warts.ArrayEquals")
) // Wartremover is complaining, don't now why :/
final case class EventEntity(
    blockHash: BlockHash,
    txHash: TransactionId,
    contractAddress: Address,
    inputAddress: Option[Address],
    timestamp: TimeStamp,
    eventIndex: Int,
    fields: ArraySeq[Val],
    eventOrder: Int
) {
  def toApi: Event = Event(
    blockHash,
    timestamp,
    txHash,
    contractAddress,
    inputAddress,
    eventIndex,
    fields
  )
}

object EventEntity {
  def from(
      blockHash: BlockHash,
      txHash: TransactionId,
      contractAddress: Address,
      inputAddress: Option[Address],
      timestamp: TimeStamp,
      eventIndex: Int,
      fields: ArraySeq[Val],
      order: Int
  ): EventEntity = {
    EventEntity(
      blockHash,
      txHash,
      contractAddress,
      inputAddress,
      timestamp,
      eventIndex,
      fields,
      order
    )
  }
}
