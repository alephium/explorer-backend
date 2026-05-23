// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.queries

import org.alephium.explorer.api.model.IntervalType
object QueryUtil {
  /*
   * The following 3 queries help averaging data over different time intervals.
   * The data at a given time is always part of it's above time interval point.
   * For example in hourly interval, data at 08:26 will be included in the value at 09:00
   * and the value at 09:00:00.001 will be averaged with the value at 10:00.
   * The idea is to have round time value, to ease the charts reading, rather than averaging at 08:59:59.999
   * Have a look at `HashrateServiceSpec` for multiple examples
   */

  private val timestampTZ: String = "to_timestamp(block_timestamp/1000.0) AT TIME ZONE 'UTC'"

  val hourlyQuery: String =
    s"DATE_TRUNC('HOUR', $timestampTZ) + ((CEILING((EXTRACT(MINUTE FROM $timestampTZ) + EXTRACT(SECOND FROM $timestampTZ)/60) / 60)) * INTERVAL '1 HOUR')"

  val dailyQuery: String =
    s"""DATE_TRUNC('DAY', $timestampTZ) +
      ((CEILING((EXTRACT(HOUR FROM $timestampTZ)*60 + EXTRACT(MINUTE FROM $timestampTZ) + EXTRACT(SECOND FROM $timestampTZ)/60)/60/24)) * INTERVAL '1 DAY')
    """

  val weeklyQuery: String =
    s"""DATE_TRUNC('WEEK', $timestampTZ) +
      ((CEILING((EXTRACT(HOUR FROM $timestampTZ)*60 + EXTRACT(MINUTE FROM $timestampTZ) + EXTRACT(SECOND FROM $timestampTZ)/60)/60/24)) * INTERVAL '7 DAY')
    """

  def extractEpoch(query: String): String =
    s"(EXTRACT(EPOCH FROM (($query) AT TIME ZONE 'UTC')) * 1000)"

  def dateGroupQuery(intervalType: IntervalType): String =
    intervalType match {
      case IntervalType.Hourly => hourlyQuery
      case IntervalType.Daily  => dailyQuery
      case IntervalType.Weekly => weeklyQuery
      case IntervalType.Monthly =>
        throw new IllegalArgumentException("Monthly interval type is not supported")
    }
}
