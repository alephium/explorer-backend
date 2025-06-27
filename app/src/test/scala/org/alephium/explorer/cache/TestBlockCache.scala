// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.cache

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import org.alephium.explorer.GroupSetting

object TestBlockCache {

  /** @return
    *   Test instance of [[BlockCache]] for faster cache reloads than configured periods in
    *   `application.conf`
    */
  def apply()(implicit
      groupSetting: GroupSetting,
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): BlockCache =
    BlockCache(
      cacheRowCountReloadPeriod = 1.second,
      cacheBlockTimesReloadPeriod = 1.second,
      cacheLatestBlocksReloadPeriod = 1.second
    )

}
