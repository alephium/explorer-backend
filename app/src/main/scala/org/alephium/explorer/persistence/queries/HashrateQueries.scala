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

import java.time._

import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import org.alephium.explorer.persistence._
import org.alephium.explorer.persistence.schema._
import org.alephium.util.TimeStamp

trait HashrateQueries extends CustomTypes {
  val config: DatabaseConfig[JdbcProfile]

  import config.profile.api._

  def getHashratesQuery(from: TimeStamp,
                        to: TimeStamp,
                        interval: Int): SqlActionSR[(TimeStamp, BigDecimal)] = {
    val fromInstant = Instant.ofEpochMilli(from.millis)
    val toInstant   = Instant.ofEpochMilli(to.millis)

    sql"""
        SELECT timestamp, value
        FROM hashrates
        WHERE interval_type = #$interval
        AND timestamp >= '#${fromInstant.toString}'
        AND timestamp <= '#${toInstant.toString}'
        ORDER BY timestamp
      """.as[(TimeStamp, BigDecimal)]
  }

  def computeHashratesAndInsert(from: TimeStamp, intervalType: Int): DBActionW[Int] = {
    val hashrates = (intervalType match {
      case 0 => compute10MinutesHashrateRawString(from)
      case 1 => computeHourlyHashrateRawString(from)
      case 2 => computeDailyHashrateRawString(from)
      case _ => "()"
    })

    sqlu"""
      INSERT INTO hashrates (timestamp, value, interval_type)
      (#$hashrates)
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
  private def compute10MinutesHashrateRawString(from: TimeStamp): String = {
    computeHashrateRawString(
      from,
      "DATE_TRUNC('HOUR', timestamp) + ((CEILING((EXTRACT(MINUTE FROM timestamp) + EXTRACT(SECOND FROM timestamp)/60)/10)*10) * INTERVAL '1 MINUTE')",
      0
    )
  }

  def compute10MinutesHashrate(from: TimeStamp): SqlActionSR[(TimeStamp, BigDecimal)] = {
    sql"#${compute10MinutesHashrateRawString(from)};".as[(TimeStamp, BigDecimal)]
  }

  private def computeHourlyHashrateRawString(from: TimeStamp): String = {
    computeHashrateRawString(
      from,
      "DATE_TRUNC('HOUR', timestamp) + ((CEILING((EXTRACT(MINUTE FROM timestamp) + EXTRACT(SECOND FROM timestamp)/60) / 60)) * INTERVAL '1 HOUR')",
      1
    )
  }

  def computeHourlyHashrate(from: TimeStamp): SqlActionSR[(TimeStamp, BigDecimal)] = {
    sql"#${computeHourlyHashrateRawString(from)};".as[(TimeStamp, BigDecimal)]
  }

  private def computeDailyHashrateRawString(from: TimeStamp): String = {
    computeHashrateRawString(
      from,
      """
        DATE_TRUNC('DAY', timestamp) +
        ((CEILING((EXTRACT(HOUR FROM timestamp)*60 + EXTRACT(MINUTE FROM timestamp) + EXTRACT(SECOND FROM timestamp)/60)/60/24)) * INTERVAL '1 DAY')
      """,
      2
    )
  }

  def computeDailyHashrate(from: TimeStamp): SqlActionSR[(TimeStamp, BigDecimal)] = {
    sql"#${computeDailyHashrateRawString(from)};".as[(TimeStamp, BigDecimal)]
  }

  private def computeHashrateRawString(from: TimeStamp,
                                       dateGroup: String,
                                       intervalType: Int): String = {
    val instant = Instant.ofEpochMilli(from.millis)
    s"""
        SELECT
        $dateGroup as date,
        AVG(hashrate),
        $intervalType
        FROM block_headers
        WHERE timestamp >= '${instant.toString}'
        AND main_chain = true
        GROUP BY date
      """
  }
}
