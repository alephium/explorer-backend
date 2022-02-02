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

import scala.concurrent.{ExecutionContext, Future}

import com.typesafe.scalalogging.StrictLogging
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import org.alephium.explorer.api.model.Hashrate
import org.alephium.explorer.persistence._
import org.alephium.explorer.persistence.model.HashrateEntity
import org.alephium.explorer.persistence.queries.HashrateQueries
import org.alephium.explorer.persistence.schema._
import org.alephium.protocol.ALPH
import org.alephium.util.{Duration, TimeStamp}

trait HashrateService extends SyncService {
  def get(from: TimeStamp, to: TimeStamp, interval: Int): Future[Seq[Hashrate]]
}

object HashrateService {

  def apply(syncPeriod: Duration, config: DatabaseConfig[JdbcProfile])(
      implicit executionContext: ExecutionContext): HashrateService =
    new Impl(syncPeriod, config)

  private class Impl(val syncPeriod: Duration, val config: DatabaseConfig[JdbcProfile])(
      implicit val executionContext: ExecutionContext)
      extends HashrateService
      with HashrateQueries
      with BlockHeaderSchema
      with HashrateSchema
      with DBRunner
      with StrictLogging {
    import config.profile.api._

    private val stepBack: Duration = Duration.ofDaysUnsafe(7)

    def syncOnce(): Future[Unit] = {
      logger.debug("Updating hashrates")
      val startedAt = TimeStamp.now()
      updateHashrates().map { _ =>
        val duration = TimeStamp.now().deltaUnsafe(startedAt)
        logger.debug(s"Hashrates updated in ${duration.millis} ms")
      }
    }

    def get(from: TimeStamp, to: TimeStamp, interval: Int): Future[Seq[Hashrate]] = {
      run(getHashratesQuery(from, to, interval)).map(_.map {
        case (timestamp, hashrate) =>
          Hashrate(timestamp, hashrate)
      })
    }

    private def updateHashrates(): Future[Unit] = {
      run(
        findLatestHashrate()
          .map {
            case Some(hashrate) => hashrate.timestamp.minusUnsafe(stepBack)
            case None           => ALPH.LaunchTimestamp
          }
          .flatMap { timestamp =>
            for {
              _ <- update1OMinutes(timestamp)
              _ <- updateHourly(timestamp)
              _ <- updateDaily(timestamp)
            } yield ()
          })
    }

    private def update1OMinutes(from: TimeStamp) = {
      updateInterval(from, 0, compute10MinutesHashrate)
    }

    private def updateHourly(from: TimeStamp) = {
      updateInterval(from, 1, computeHourlyHashrate)
    }

    private def updateDaily(from: TimeStamp) = {
      updateInterval(from, 2, computeDailyHashrate)
    }

    private def updateInterval(from: TimeStamp,
                               interval: Int,
                               select: TimeStamp => DBActionSR[(TimeStamp, BigDecimal)]) = {
      select(from).flatMap { hashrates =>
        val values = hashrates.map {
          case (timestamp, hashrate) =>
            (timestamp, hashrate)
        }
        insert(values, interval)
      }
    }

    private def findLatestHashrate(): DBActionR[Option[HashrateEntity]] = {
      hashrateTable
        .sortBy(_.timestamp.desc)
        .result
        .headOption
    }
  }
}
