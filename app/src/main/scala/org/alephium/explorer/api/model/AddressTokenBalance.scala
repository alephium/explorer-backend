// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.api.model

import org.alephium.explorer.api.Json._
import org.alephium.json.Json._
import org.alephium.protocol.model.TokenId
import org.alephium.util.U256

final case class AddressTokenBalance(tokenId: TokenId, balance: U256, lockedBalance: U256)

object AddressTokenBalance {
  implicit val readWriter: ReadWriter[AddressTokenBalance] = macroRW
}
