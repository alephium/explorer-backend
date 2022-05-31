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

package org.alephium.explorer.util

import java.time._
import java.time.temporal.ChronoUnit

import org.alephium.util.TimeStamp

object TimeUtil {

  /** Convert's [[java.time.OffsetTime]] to [[java.time.ZonedDateTime]] in the same zone */
  @inline def toZonedDateTime(time: OffsetTime): ZonedDateTime =
    time.atDate(LocalDate.now(time.getOffset)).toZonedDateTime

  @inline def toInstant(timestamp: TimeStamp): Instant =
    Instant.ofEpochMilli(timestamp.millis)

  def truncatedToDay(timestamp: TimeStamp): TimeStamp =
    mapInstant(timestamp)(_.truncatedTo(ChronoUnit.DAYS))

  def truncatedToHour(timestamp: TimeStamp): TimeStamp =
    mapInstant(timestamp)(_.truncatedTo(ChronoUnit.HOURS))

  private def mapInstant(timestamp: TimeStamp)(f: Instant => Instant): TimeStamp = {
    val instant = toInstant(timestamp)
    TimeStamp
      .unsafe(
        f(instant).toEpochMilli
      )
  }

}
