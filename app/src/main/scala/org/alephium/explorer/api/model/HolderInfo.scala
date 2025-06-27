// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.api.model

import org.alephium.explorer.api.Json._
import org.alephium.json.Json._
import org.alephium.protocol.model.Address
import org.alephium.util.U256

final case class HolderInfo(address: Address, balance: U256)

object HolderInfo {
  implicit val readWriter: ReadWriter[HolderInfo] = macroRW
}
