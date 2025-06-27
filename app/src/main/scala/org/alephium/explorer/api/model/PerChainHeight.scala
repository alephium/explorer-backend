// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.api.model

import org.alephium.json.Json._

@SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
final case class PerChainHeight(
    chainFrom: Int,
    chainTo: Int,
    height: Long,
    value: Long // TODO Remove once front-end is using `height`
)

object PerChainHeight {
  implicit val readWriter: ReadWriter[PerChainHeight] = macroRW
}
