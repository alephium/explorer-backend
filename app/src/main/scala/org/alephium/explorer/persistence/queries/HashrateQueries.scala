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

import scala.concurrent.ExecutionContext

import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.api.model.IntervalType
import org.alephium.explorer.persistence._
import org.alephium.explorer.persistence.schema.CustomGetResult._
import org.alephium.explorer.persistence.schema.CustomSetParameter._
import org.alephium.explorer.util.SlickUtil._
import org.alephium.util.{AVector, TimeStamp}

object HashrateQueries {

  def getHashratesQuery(from: TimeStamp,
                        to: TimeStamp,
                        intervalType: IntervalType): DBActionSR[(TimeStamp, BigDecimal)] = {

    sql"""
        SELECT block_timestamp, value
        FROM hashrates
        WHERE interval_type = ${intervalType.value}
        AND block_timestamp >= $from
        AND block_timestamp <= $to
        ORDER BY block_timestamp
      """.asAV[(TimeStamp, BigDecimal)]
  }

  def computeHashratesAndInsert(from: TimeStamp, intervalType: IntervalType): DBActionW[Int] = {
    val dateGroup = intervalType match {
      case IntervalType.Hourly => hourlyQuery
      case IntervalType.Daily  => dailyQuery
    }

    sqlu"""
        INSERT INTO hashrates (block_timestamp, value, interval_type)
        SELECT
        EXTRACT(EPOCH FROM ((#$dateGroup) AT TIME ZONE 'UTC')) * 1000 as ts,
        AVG(hashrate),
        ${intervalType.value}
        FROM block_headers
        WHERE block_timestamp >= $from
        AND main_chain = true
        GROUP BY ts
        ON CONFLICT (block_timestamp, interval_type) DO UPDATE
        SET value = EXCLUDED.value
      """
  }

  /*
   * The following 3 queries are averaging the hashrates from the block_header table over different time intervals.
   * An hashrate at a given time is always part of it's above time interval point.
   * For example in hourly interval, the hashrate of a block at 08:26 will be included in the value at 09:00
   * and the value at 09:00:00.001 will be averaged with the value at 10:00.
   * The idea is to have round time value, to ease the charts reading, rather than averaging at 08:59:59.999
   * Have a look at `HashrateServiceSpec` for multiple examples
   */

  private val timestampTZ = "to_timestamp(block_timestamp/1000.0) AT TIME ZONE 'UTC'"

  private val hourlyQuery =
    s"DATE_TRUNC('HOUR', $timestampTZ) + ((CEILING((EXTRACT(MINUTE FROM $timestampTZ) + EXTRACT(SECOND FROM $timestampTZ)/60) / 60)) * INTERVAL '1 HOUR')"

  private val dailyQuery =
    s"""DATE_TRUNC('DAY', $timestampTZ) +
      ((CEILING((EXTRACT(HOUR FROM $timestampTZ)*60 + EXTRACT(MINUTE FROM $timestampTZ) + EXTRACT(SECOND FROM $timestampTZ)/60)/60/24)) * INTERVAL '1 DAY')
    """

  private def computeHourlyHashrateRawString(from: TimeStamp) = {
    computeHashrateRawString(
      from,
      hourlyQuery,
      IntervalType.Hourly
    )
  }

  def computeHourlyHashrate(from: TimeStamp)(
      implicit ec: ExecutionContext): DBActionR[AVector[(TimeStamp, BigDecimal)]] = {
    computeHourlyHashrateRawString(from).map(_.map { case (ts, v, _) => (ts, v) })
  }

  private def computeDailyHashrateRawString(from: TimeStamp) = {
    computeHashrateRawString(
      from,
      dailyQuery,
      IntervalType.Daily
    )
  }

  def computeDailyHashrate(from: TimeStamp)(
      implicit ec: ExecutionContext): DBActionR[AVector[(TimeStamp, BigDecimal)]] = {
    val sql = computeDailyHashrateRawString(from)
    sql.map(_.map { case (ts, v, _) => (ts, v) })
  }

  private def computeHashrateRawString(from: TimeStamp,
                                       dateGroup: String,
                                       intervalType: IntervalType) = {
    sql"""
        SELECT
        EXTRACT(EPOCH FROM ((#$dateGroup) AT TIME ZONE 'UTC')) * 1000 as ts,
        AVG(hashrate),
        ${intervalType.value}
        FROM block_headers
        WHERE block_timestamp >= $from
        AND main_chain = true
        GROUP BY ts
      """.asAV[(TimeStamp, BigDecimal, Int)]
  }
}
