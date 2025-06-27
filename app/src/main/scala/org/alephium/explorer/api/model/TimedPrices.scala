// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.api.model

import scala.collection.immutable.ArraySeq

import org.alephium.api.UtilJson.{timestampReader, timestampWriter}
import org.alephium.json.Json._
import org.alephium.util.TimeStamp

@SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
final case class TimedPrices(
    timestamps: ArraySeq[TimeStamp],
    prices: ArraySeq[Double]
)

object TimedPrices {
  implicit val readWriter: ReadWriter[TimedPrices] = macroRW
}
