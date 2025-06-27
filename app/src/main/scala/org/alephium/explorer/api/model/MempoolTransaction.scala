// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.api.model

import scala.collection.immutable.ArraySeq

import org.alephium.api.UtilJson.{timestampReader, timestampWriter}
import org.alephium.explorer.api.Json._
import org.alephium.json.Json._
import org.alephium.protocol.model.{GroupIndex, TransactionId}
import org.alephium.util.{TimeStamp, U256}

final case class MempoolTransaction(
    hash: TransactionId,
    chainFrom: GroupIndex,
    chainTo: GroupIndex,
    inputs: ArraySeq[Input],
    outputs: ArraySeq[Output],
    gasAmount: Int,
    gasPrice: U256,
    lastSeen: TimeStamp
)

object MempoolTransaction {
  implicit val utxRW: ReadWriter[MempoolTransaction] = macroRW
}
