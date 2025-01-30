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

package org.alephium.explorer.service

import scala.collection.immutable.ArraySeq
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration

import com.typesafe.scalalogging.StrictLogging
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.api.model.{Hashrate, IntervalType}
import org.alephium.explorer.persistence._
import org.alephium.explorer.persistence.DBRunner._
import org.alephium.explorer.persistence.queries.HashrateQueries._
import org.alephium.explorer.persistence.schema.CustomGetResult._
import org.alephium.explorer.persistence.schema.CustomSetParameter._
import org.alephium.explorer.util.Scheduler
import org.alephium.explorer.util.SlickUtil._
import org.alephium.explorer.util.TimeUtil._
import org.alephium.protocol.ALPH
import org.alephium.util.{Duration, TimeStamp}

case object HashrateService extends StrictLogging {

  val hourlyStepBack: Duration = Duration.ofHoursUnsafe(2)
  val dailyStepBack: Duration  = Duration.ofDaysUnsafe(1)
  val syncDelay: Duration      = Duration.ofMinutesUnsafe(1)

  /*
   * Hashrate are computed once on start. As hashrates are grouped by hourly/daily interval
   * we can schedule the next hashrate computation on next round hour + a delay
   * e.g if it's 08:20, next sync will occurs at 09:00 + syncDelay
   */
  def start(interval: FiniteDuration)(implicit
      executionContext: ExecutionContext,
      databaseConfig: DatabaseConfig[PostgresProfile],
      scheduler: Scheduler
  ): Future[Unit] = {
    scheduler.scheduleLoop(
      taskId = HashrateService.productPrefix,
      firstInterval = computeFirstSyncInterval(TimeStamp.now()).asScala,
      loopInterval = interval
    )(syncOnce())
  }

  def syncOnce()(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[Unit] = {
    logger.debug("Updating hashrates")
    val startedAt = TimeStamp.now()
    updateHashrates().map { _ =>
      val duration = TimeStamp.now().deltaUnsafe(startedAt)
      logger.debug(s"Hashrates updated in ${duration.millis} ms")
    }
  }

  def get(from: TimeStamp, to: TimeStamp, intervalType: IntervalType)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[Hashrate]] =
    run(getHashratesQuery(from, to, intervalType)).map(_.map { case (timestamp, hashrate) =>
      Hashrate(timestamp, hashrate, hashrate)
    })

  private def updateHashrates()(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[Unit] =
    run(
      for {
        hourlyTs <- findLatestHashrateAndStepBack(IntervalType.Hourly, computeHourlyStepBack)
        dailyTs  <- findLatestHashrateAndStepBack(IntervalType.Daily, computeDailyStepBack)
        _        <- computeHashratesAndInsert(hourlyTs, IntervalType.Hourly)
        _        <- computeHashratesAndInsert(dailyTs, IntervalType.Daily)
      } yield ()
    )

  private def findLatestHashrate(intervalType: IntervalType)(implicit
      ec: ExecutionContext
  ): DBActionR[Option[TimeStamp]] =
    sql"""
      SELECT block_timestamp
      FROM hashrates
      WHERE interval_type = $intervalType
      ORDER BY block_timestamp DESC
      LIMIT 1
    """.asAS[TimeStamp].headOrNone

  private def findLatestHashrateAndStepBack(
      intervalType: IntervalType,
      computeStepBack: TimeStamp => TimeStamp
  )(implicit ec: ExecutionContext): DBActionR[TimeStamp] = {
    findLatestHashrate(intervalType).map(
      _.map(timestamp => computeStepBack(timestamp)).getOrElse(ALPH.LaunchTimestamp)
    )
  }

  /*
   * We truncate to a round value and add 1 millisecond to be sure
   * to recompute a complete time step and not step back in the middle of it.
   */

  def computeHourlyStepBack(timestamp: TimeStamp): TimeStamp =
    truncatedToHour(timestamp.minusUnsafe(hourlyStepBack)).plusMillisUnsafe(1)

  def computeDailyStepBack(timestamp: TimeStamp): TimeStamp =
    truncatedToDay(timestamp.minusUnsafe(dailyStepBack)).plusMillisUnsafe(1)

  def computeFirstSyncInterval(from: TimeStamp): Duration = {
    val truncated = truncatedToHour(from).plusUnsafe(syncDelay)
    if (from == truncated) {
      Duration.zero
    } else if (from.isBefore(truncated)) {
      truncated.deltaUnsafe(from)
    } else {
      truncated.plusHoursUnsafe(1L).deltaUnsafe(from)
    }
  }
}
