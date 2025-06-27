// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.api.model

import org.alephium.json.Json._

@SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
final case class PerChainCount(
    chainFrom: Int,
    chainTo: Int,
    count: Long
)

object PerChainCount {
  implicit val readWriter: ReadWriter[PerChainCount] = macroRW
}
