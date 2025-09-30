// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.api.model

import scala.collection.immutable.ArraySeq

import sttp.tapir.Schema

import org.alephium.api.TapirSchemas._
import org.alephium.explorer.api.Json._
import org.alephium.json.Json._
import org.alephium.protocol.model.TransactionId
import org.alephium.util.U256

final case class UnsignedTransaction(
    hash: TransactionId,
    version: Byte,
    networkId: Byte,
    scriptOpt: Option[String],
    gasAmount: Int,
    gasPrice: U256,
    inputs: ArraySeq[Input],
    outputs: ArraySeq[Output]
)

object UnsignedTransaction {
  implicit val unsignedTxRW: ReadWriter[UnsignedTransaction] = macroRW
  implicit val schema: Schema[UnsignedTransaction]           = Schema.derived[UnsignedTransaction]
}
