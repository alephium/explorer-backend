// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.api.model

import org.alephium.api.UtilJson._
import org.alephium.explorer.api.Json.u256ReadWriter
import org.alephium.json.Json._
import org.alephium.util.{TimeStamp, U256}

final case class TokenSupply(
    timestamp: TimeStamp,
    total: U256,
    circulating: U256,
    reserved: U256,
    locked: U256,
    maximum: U256
)

object TokenSupply {
  implicit val readWriter: ReadWriter[TokenSupply] = macroRW
}
