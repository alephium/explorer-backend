// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.api.model

import scala.collection.immutable.ArraySeq

import sttp.tapir.Schema

import org.alephium.json.Json._

@SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
final case class AmountHistory(
    amountHistory: ArraySeq[TimedAmount]
)

object AmountHistory {
  implicit val readWriter: ReadWriter[AmountHistory] = macroRW[AmountHistory]
  implicit val schema: Schema[AmountHistory]         = Schema.derived[AmountHistory]
}
