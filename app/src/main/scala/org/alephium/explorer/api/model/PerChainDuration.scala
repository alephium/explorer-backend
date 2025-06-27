// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.api.model

import org.alephium.json.Json._

@SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
final case class PerChainDuration(
    chainFrom: Int,
    chainTo: Int,
    duration: Long,
    value: Long // TODO Remove once front-end is using `duration`
)

object PerChainDuration {
  implicit val readWriter: ReadWriter[PerChainDuration] = macroRW
}
