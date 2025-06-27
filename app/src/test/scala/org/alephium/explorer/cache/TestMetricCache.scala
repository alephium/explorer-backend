// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.cache

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

import org.alephium.explorer.persistence.Database

object TestMetricCache {

  def apply(database: Database)(implicit
      ec: ExecutionContext
  ): MetricCache =
    new MetricCache(
      database = database,
      reloadPeriod = 1.second
    )

}
