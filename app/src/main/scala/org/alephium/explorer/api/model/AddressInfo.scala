// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.api.model

import org.alephium.explorer.api.Json.u256ReadWriter
import org.alephium.json.Json._
import org.alephium.util.U256

final case class AddressInfo(balance: U256, lockedBalance: U256, txNumber: Int)

object AddressInfo {
  implicit val readWriter: ReadWriter[AddressInfo] = macroRW
}
