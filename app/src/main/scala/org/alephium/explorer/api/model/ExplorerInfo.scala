// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.api.model

import org.alephium.api.UtilJson._
import org.alephium.json.Json._
import org.alephium.util.TimeStamp

final case class ExplorerInfo(
    releaseVersion: String,
    commit: String,
    migrationsVersion: Int,
    lastFinalizedInputTime: TimeStamp,
    lastHoldersUpdate: TimeStamp
)

object ExplorerInfo {
  implicit val readWriter: ReadWriter[ExplorerInfo] = macroRW
}
