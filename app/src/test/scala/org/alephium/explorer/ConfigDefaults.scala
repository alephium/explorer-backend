// Copyright 2018 The Alephium Authors
// This file is part of the alephium project.
//
// The library is free software: you can redistribute it and/or modify
// it under the terms of the GNU Lesser General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// The library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public License
// along with the library. If not, see <http://www.gnu.org/licenses/>.

package org.alephium.explorer

import java.time.LocalTime

import org.alephium.explorer.config.ExplorerConfig._
import org.alephium.protocol.config.GroupConfig
import org.alephium.util.Duration

object ConfigDefaults {
  implicit val groupSetting: GroupSetting = Generators.groupSettingGen.sample.get
  implicit val groupConfig: GroupConfig   = groupSetting.groupConfig

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

  val servicesConfig: Services = Services(
    Services.BlockflowSync(
      syncPeriod = Duration.ofSecondsUnsafe(5).asScala
    ),
    Services.MempoolSync(
      enable = true,
      syncPeriod = Duration.ofSecondsUnsafe(5).asScala
    ),
    Services.TokenSupply(
      enable = true,
      scheduleTime = LocalTime.parse("02:00")
    ),
    Services.Hashrate(
      enable = true,
      syncPeriod = Duration.ofMinutesUnsafe(60).asScala
    ),
    Services.TxHistory(
      enable = true,
      syncPeriod = Duration.ofMinutesUnsafe(15).asScala
    ),
    Services.Finalizer(
      syncPeriod = Duration.ofMinutesUnsafe(10).asScala
    ),
    Services.Holder(
      enable = true,
      scheduleTime = LocalTime.parse("02:00")
    )
  )
}
