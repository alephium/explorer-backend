// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.api.model

import org.alephium.api.UtilJson.{timestampReader, timestampWriter}
import org.alephium.json.Json._
import org.alephium.util.{TimeStamp}

@SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
final case class Hashrate(
    timestamp: TimeStamp,
    hashrate: BigDecimal,
    value: BigDecimal
)

object Hashrate {
  implicit val readWriter: ReadWriter[Hashrate] = macroRW
}
