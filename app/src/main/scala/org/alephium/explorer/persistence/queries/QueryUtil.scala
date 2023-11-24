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

  def dateGroupQuery(intervalType: IntervalType): String =
    intervalType match {
      case IntervalType.Hourly => hourlyQuery
      case IntervalType.Daily  => dailyQuery
    }
}
