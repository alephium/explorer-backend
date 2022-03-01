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

import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import org.alephium.explorer.api.model.IntervalType
import org.alephium.explorer.persistence._
import org.alephium.explorer.persistence.schema._
import org.alephium.util.TimeStamp

trait HashrateQueries extends CustomTypes {
  val config: DatabaseConfig[JdbcProfile]

  import config.profile.api._

  def getHashratesQuery(from: TimeStamp,
                        to: TimeStamp,
                        intervalType: IntervalType): DBActionSR[(TimeStamp, BigDecimal)] = {

    sql"""
        SELECT timestamp, value
        FROM hashrates
        WHERE interval_type = ${intervalType.value}
        AND timestamp >= $from
        AND timestamp <= $to
        ORDER BY timestamp
      """.as[(TimeStamp, BigDecimal)]
  }

  def computeHashratesAndInsert(from: TimeStamp, intervalType: IntervalType): DBActionW[Int] = {
    val dateGroup = intervalType match {
      case IntervalType.TenMinutes => tenMinutesQuery
      case IntervalType.Hourly     => hourlyQuery
      case IntervalType.Daily      => dailyQuery
    }

    sqlu"""
        INSERT INTO hashrates (timestamp, value, interval_type)
        SELECT
        EXTRACT(EPOCH FROM ((#$dateGroup) AT TIME ZONE 'UTC')) * 1000 as ts,
        AVG(hashrate),
        ${intervalType.value}
        FROM block_headers
        WHERE timestamp >= $from
        AND main_chain = true
        GROUP BY ts
        ON CONFLICT (timestamp, interval_type) DO UPDATE
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
   *
   * 10 minutes interval example with 8:26:05.832
   * trucate hour: 8:00:00
   * minutes + seconds as minutes: 26 + 5.832 / 60 = 26.09
   * round to above 10 minutes: ceiling(26.09 / 10) * 10 = 30
   * result: 8:30
   */

  private val timestampTZ = "to_timestamp(timestamp/1000.0) AT TIME ZONE 'UTC'"

  private val tenMinutesQuery =
    s"DATE_TRUNC('HOUR', $timestampTZ) + ((CEILING((EXTRACT(MINUTE FROM $timestampTZ) + EXTRACT(SECOND FROM $timestampTZ)/60)/10)*10) * INTERVAL '1 MINUTE')"

  private val hourlyQuery =
    s"DATE_TRUNC('HOUR', $timestampTZ) + ((CEILING((EXTRACT(MINUTE FROM $timestampTZ) + EXTRACT(SECOND FROM $timestampTZ)/60) / 60)) * INTERVAL '1 HOUR')"

  private val dailyQuery =
    s"""DATE_TRUNC('DAY', $timestampTZ) +
      ((CEILING((EXTRACT(HOUR FROM $timestampTZ)*60 + EXTRACT(MINUTE FROM $timestampTZ) + EXTRACT(SECOND FROM $timestampTZ)/60)/60/24)) * INTERVAL '1 DAY')
    """

  private def compute10MinutesHashrateRawString(from: TimeStamp) = {
    computeHashrateRawString(
      from,
      tenMinutesQuery,
      IntervalType.TenMinutes
    )
  }

  def compute10MinutesHashrate(from: TimeStamp)(
      implicit ec: ExecutionContext): DBActionR[Vector[(TimeStamp, BigDecimal)]] = {
    compute10MinutesHashrateRawString(from).map(_.map { case (ts, v, _) => (ts, v) })
  }

  private def computeHourlyHashrateRawString(from: TimeStamp) = {
    computeHashrateRawString(
      from,
      hourlyQuery,
      IntervalType.Hourly
    )
  }

  def computeHourlyHashrate(from: TimeStamp)(
      implicit ec: ExecutionContext): DBActionR[Vector[(TimeStamp, BigDecimal)]] = {
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
      implicit ec: ExecutionContext): DBActionR[Vector[(TimeStamp, BigDecimal)]] = {
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
        WHERE timestamp >= $from
        AND main_chain = true
        GROUP BY ts
      """.as[(TimeStamp, BigDecimal, Int)]
  }
}
