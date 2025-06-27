// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.api.model

import scala.collection.immutable.ArraySeq

import org.alephium.api.UtilJson._
import org.alephium.api.model.Val
import org.alephium.explorer.api.Codecs._
import org.alephium.json.Json._
import org.alephium.protocol.model.{Address, BlockHash, TransactionId}
import org.alephium.util.TimeStamp

final case class Event(
    blockHash: BlockHash,
    timestamp: TimeStamp,
    txHash: TransactionId,
    contractAddress: Address,
    inputAddress: Option[Address],
    eventIndex: Int,
    fields: ArraySeq[Val]
)

object Event {
  implicit val codec: ReadWriter[Event] = macroRW
}
