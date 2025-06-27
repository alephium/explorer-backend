// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer

import org.alephium.explorer.config.Default
import org.alephium.explorer.config.ExplorerConfig._
import org.alephium.protocol.config.GroupConfig
import org.alephium.util.{Duration, TimeStamp}

object ConfigDefaults {
  implicit val groupSetting: GroupSetting = GroupSetting(
    Default.groupConfig.groups
  )
  implicit val groupConfig: GroupConfig = Default.groupConfig

  implicit val consensus: Consensus = Consensus(
    mainnet = Consensus.Setting(TimeStamp.zero, Duration.ofSecondsUnsafe(64)),
    rhone = Consensus.Setting(TimeStamp.zero, Duration.ofSecondsUnsafe(16)),
    danube = Consensus.Setting(TimeStamp.zero, Duration.ofSecondsUnsafe(8))
  )

  val maxTimeIntervals: MaxTimeIntervals =
    MaxTimeIntervals(
      amountHistory = MaxTimeInterval(
        hourly = Duration.ofDaysUnsafe(7),
        daily = Duration.ofDaysUnsafe(366),
        weekly = Duration.ofDaysUnsafe(366)
      ),
      charts = MaxTimeInterval(
        hourly = Duration.ofDaysUnsafe(30),
        daily = Duration.ofDaysUnsafe(366),
        weekly = Duration.ofDaysUnsafe(366)
      ),
      exportTxs = Duration.ofDaysUnsafe(366)
    )
}
