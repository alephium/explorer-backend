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

import java.time.Instant
import java.time.temporal.ChronoUnit

import scala.concurrent.{ExecutionContext, Future}

import com.typesafe.scalalogging.StrictLogging
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.api.model.{Hashrate, IntervalType}
import org.alephium.explorer.persistence._
import org.alephium.explorer.persistence.model.HashrateEntity
import org.alephium.explorer.persistence.queries.HashrateQueries
import org.alephium.explorer.persistence.schema._
import org.alephium.protocol.ALPH
import org.alephium.util.{Duration, TimeStamp}

trait HashrateService extends SyncService {
  def get(from: TimeStamp, to: TimeStamp, intervalType: IntervalType): Future[Seq[Hashrate]]
}

object HashrateService {

  val hourlyStepBack: Duration = Duration.ofHoursUnsafe(2)
  val dailyStepBack: Duration  = Duration.ofDaysUnsafe(1)

  def apply(syncPeriod: Duration, databaseConfig: DatabaseConfig[PostgresProfile])(
      implicit executionContext: ExecutionContext): HashrateService =
    new Impl(syncPeriod, databaseConfig)

  private class Impl(val syncPeriod: Duration, val databaseConfig: DatabaseConfig[PostgresProfile])(
      implicit val executionContext: ExecutionContext)
      extends HashrateService
      with HashrateQueries
      with DBRunner
      with StrictLogging {

    def syncOnce(): Future[Unit] = {
      logger.debug("Updating hashrates")
      val startedAt = TimeStamp.now()
      updateHashrates().map { _ =>
        val duration = TimeStamp.now().deltaUnsafe(startedAt)
        logger.debug(s"Hashrates updated in ${duration.millis} ms")
      }
    }

    def get(from: TimeStamp, to: TimeStamp, intervalType: IntervalType): Future[Seq[Hashrate]] = {
      run(getHashratesQuery(from, to, intervalType)).map(_.map {
        case (timestamp, hashrate) =>
          Hashrate(timestamp, hashrate)
      })
    }

    private def updateHashrates(): Future[Unit] = {
      run(
        for {
          hourlyTs <- findLatestHashrateAndStepBack(IntervalType.Hourly, computeHourlyStepBack)
          dailyTs  <- findLatestHashrateAndStepBack(IntervalType.Daily, computeDailyStepBack)
          _        <- computeHashratesAndInsert(hourlyTs, IntervalType.Hourly)
          _        <- computeHashratesAndInsert(dailyTs, IntervalType.Daily)
        } yield ()
      )
    }

    private def findLatestHashrate(
        intervalType: IntervalType): DBActionR[Option[HashrateEntity]] = {
      HashrateSchema.hashrateTable
        .filter(_.intervalType === intervalType)
        .sortBy(_.timestamp.desc)
        .result
        .headOption
    }

    private def findLatestHashrateAndStepBack(
        intervalType: IntervalType,
        computeStepBack: TimeStamp => TimeStamp): DBActionR[TimeStamp] = {
      findLatestHashrate(intervalType).map(
        _.map(h => computeStepBack(h.timestamp)).getOrElse(ALPH.LaunchTimestamp))
    }
  }

  /*
   * We truncate to a round value and add 1 millisecond to be sure
   * to recompute a complete time step and not step back in the middle of it.
   */

  def computeHourlyStepBack(timestamp: TimeStamp): TimeStamp = {
    truncatedToHour(timestamp.minusUnsafe(hourlyStepBack)).plusMillisUnsafe(1)
  }

  def computeDailyStepBack(timestamp: TimeStamp): TimeStamp = {
    truncatedToDay(timestamp.minusUnsafe(dailyStepBack)).plusMillisUnsafe(1)
  }

  private def mapInstant(timestamp: TimeStamp)(f: Instant => Instant): TimeStamp = {
    val instant = Instant.ofEpochMilli(timestamp.millis)
    TimeStamp
      .unsafe(
        f(instant).toEpochMilli
      )
  }

  private def truncatedToHour(timestamp: TimeStamp): TimeStamp = {
    mapInstant(timestamp)(_.truncatedTo(ChronoUnit.HOURS))
  }

  private def truncatedToDay(timestamp: TimeStamp): TimeStamp = {
    mapInstant(timestamp)(_.truncatedTo(ChronoUnit.DAYS))
  }
}
