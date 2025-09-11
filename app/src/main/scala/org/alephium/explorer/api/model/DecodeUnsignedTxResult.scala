// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.api.model

import sttp.tapir.Schema

import org.alephium.api.TapirSchemas._
import org.alephium.explorer.api.Json._
import org.alephium.json.Json._
import org.alephium.protocol.model.GroupIndex

final case class DecodeUnsignedTxResult(
    fromGroup: GroupIndex,
    toGroup: GroupIndex,
    unsignedTx: UnsignedTransaction
)

object DecodeUnsignedTxResult {
  implicit val decodeResultRW: ReadWriter[DecodeUnsignedTxResult] = macroRW
  implicit val schema: Schema[DecodeUnsignedTxResult] = Schema.derived[DecodeUnsignedTxResult]
}
