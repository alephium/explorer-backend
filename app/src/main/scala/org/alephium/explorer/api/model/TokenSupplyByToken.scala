// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.api.model

import org.alephium.api.UtilJson._
import org.alephium.explorer.api.Json._
import org.alephium.json.Json._
import org.alephium.protocol.model.TokenId
import org.alephium.util.{TimeStamp, U256}

final case class TokenSupplyByToken(token: TokenId, timestamp: TimeStamp, total: U256)

object TokenSupplyByToken {
  implicit val readWriter: ReadWriter[TokenSupplyByToken] = macroRW
}
