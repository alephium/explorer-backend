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

import scala.collection.immutable.ArraySeq
import scala.concurrent.ExecutionContext

import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.api.model.IntervalType
import org.alephium.explorer.persistence._
import org.alephium.explorer.persistence.schema.CustomGetResult._
import org.alephium.explorer.persistence.schema.CustomSetParameter._
import org.alephium.explorer.util.SlickUtil._
import org.alephium.util.TimeStamp

object HashrateQueries {

  def getHashratesQuery(
      from: TimeStamp,
      to: TimeStamp,
      intervalType: IntervalType
  ): DBActionSR[(TimeStamp, BigDecimal)] = {

    sql"""
        SELECT block_timestamp, value
        FROM hashrates
        WHERE interval_type = ${intervalType.value}
        AND block_timestamp >= $from
        AND block_timestamp <= $to
        ORDER BY block_timestamp
      """.asAS[(TimeStamp, BigDecimal)]
  }

  def computeHashratesAndInsert(from: TimeStamp, intervalType: IntervalType): DBActionW[Int] = {
    val dateGroup = QueryUtil.dateGroupQuery(intervalType)

    sqlu"""
        INSERT INTO hashrates (block_timestamp, value, interval_type)
        SELECT
        #${QueryUtil.extractEpoch(dateGroup)} as ts,
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

  private def computeHourlyHashrateRawString(from: TimeStamp) = {
    computeHashrateRawString(
      from,
      QueryUtil.hourlyQuery,
      IntervalType.Hourly
    )
  }

  def computeHourlyHashrate(
      from: TimeStamp
  )(implicit ec: ExecutionContext): DBActionR[ArraySeq[(TimeStamp, BigDecimal)]] = {
    computeHourlyHashrateRawString(from).map(_.map { case (ts, v, _) => (ts, v) })
  }

  private def computeDailyHashrateRawString(from: TimeStamp) = {
    computeHashrateRawString(
      from,
      QueryUtil.dailyQuery,
      IntervalType.Daily
    )
  }

  def computeDailyHashrate(
      from: TimeStamp
  )(implicit ec: ExecutionContext): DBActionR[ArraySeq[(TimeStamp, BigDecimal)]] = {
    val sql = computeDailyHashrateRawString(from)
    sql.map(_.map { case (ts, v, _) => (ts, v) })
  }

  private def computeHashrateRawString(
      from: TimeStamp,
      dateGroup: String,
      intervalType: IntervalType
  ) = {
    sql"""
        SELECT
        #${QueryUtil.extractEpoch(dateGroup)} as ts,
        AVG(hashrate),
        ${intervalType.value}
        FROM block_headers
        WHERE block_timestamp >= $from
        AND main_chain = true
        GROUP BY ts
      """.asAS[(TimeStamp, BigDecimal, Int)]
  }
}
