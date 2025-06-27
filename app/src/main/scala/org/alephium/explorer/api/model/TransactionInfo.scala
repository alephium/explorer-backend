// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.api.model

import org.alephium.api.UtilJson._
import org.alephium.explorer.api.Json._
import org.alephium.json.Json._
import org.alephium.protocol.model.{BlockHash, TransactionId}
import org.alephium.util.TimeStamp

final case class TransactionInfo(
    hash: TransactionId,
    blockHash: BlockHash,
    timestamp: TimeStamp,
    coinbase: Boolean
)

object TransactionInfo {
  implicit val readWriter: ReadWriter[TransactionInfo] = macroRW
}
