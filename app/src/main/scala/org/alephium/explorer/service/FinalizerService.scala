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
import scala.concurrent.duration.{Duration => ScalaDuration, FiniteDuration}

import com.typesafe.scalalogging.StrictLogging
import slick.basic.DatabaseConfig
import slick.dbio.DBIOAction
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.foldFutures
import org.alephium.explorer.persistence._
import org.alephium.explorer.persistence.DBRunner._
import org.alephium.explorer.persistence.model.AppState.LastFinalizedInputTime
import org.alephium.explorer.persistence.queries.AppStateQueries
import org.alephium.explorer.persistence.schema.CustomGetResult._
import org.alephium.explorer.persistence.schema.CustomSetParameter._
import org.alephium.explorer.util.{Scheduler, TimeUtil}
import org.alephium.explorer.util.SlickUtil._
import org.alephium.util.{Duration, TimeStamp}

/*
 * Syncing mempool
 */

case object FinalizerService extends StrictLogging {

  // scalastyle:off magic.number
  val finalizationDuration: Duration = Duration.ofSecondsUnsafe(6500)
  def finalizationTime: TimeStamp    = TimeStamp.now().minusUnsafe(finalizationDuration)
  def rangeStep: Duration            = Duration.ofHoursUnsafe(24)
  // scalastyle:on magic.number

  def start(interval: FiniteDuration)(implicit ec: ExecutionContext,
                                      dc: DatabaseConfig[PostgresProfile],
                                      scheduler: Scheduler): Future[Unit] =
    scheduler.scheduleLoop(
      taskId        = FinalizerService.productPrefix,
      firstInterval = ScalaDuration.Zero,
      loopInterval  = interval
    )(syncOnce())

  def syncOnce()(implicit ec: ExecutionContext,
                 dc: DatabaseConfig[PostgresProfile]): Future[Unit] = {
    logger.debug("Finalizing")
    finalizeOutputs()
  }

  def finalizeOutputs()(implicit ec: ExecutionContext,
                        dc: DatabaseConfig[PostgresProfile]): Future[Unit] =
    run(getStartEndTime()).flatMap {
      case Some((start, end)) =>
        finalizeOutputsWith(start, end, rangeStep)
      case None =>
        Future.successful(())
    }

  def finalizeOutputsWith(start: TimeStamp, end: TimeStamp, step: Duration)(
      implicit executionContext: ExecutionContext,
      databaseConfig: DatabaseConfig[PostgresProfile]): Future[Unit] = {
    var updateCounter = 0
    logger.debug(s"Updating outputs")
    val timeRanges =
      TimeUtil.buildTimestampRange(start, end, step)
    foldFutures(timeRanges) {
      case (from, to) =>
        logger.debug(s"Updating outputs: ${TimeUtil.toInstant(from)} - ${TimeUtil.toInstant(to)}")
        run(
          (
            for {
              nb <- updateOutputs(from, to)
              _  <- updateTokenOutputs(from, to)
              _  <- updateLastFinalizedInputTime(to)
            } yield nb
          ).transactionally
        ).map { nb =>
          updateCounter = updateCounter + nb
          logger.debug(s"$updateCounter outputs updated")
        }
    }.map(_ => logger.debug(s"Outputs updated"))
  }

  private def updateOutputs(from: TimeStamp, to: TimeStamp): DBActionR[Int] =
    sqlu"""
      UPDATE outputs o
      SET spent_finalized = i.tx_hash, spent_timestamp = i.block_timestamp
      FROM inputs i
      WHERE i.output_ref_key = o.key
      AND o.main_chain=true
      AND i.main_chain=true
      AND i.block_timestamp >= $from
      AND i.block_timestamp <= $to;
      """

  private def updateTokenOutputs(from: TimeStamp, to: TimeStamp): DBActionR[Int] =
    sqlu"""
      UPDATE token_outputs o
      SET spent_finalized = i.tx_hash, spent_timestamp = i.block_timestamp
      FROM inputs i
      WHERE i.output_ref_key = o.key
      AND o.main_chain=true
      AND i.main_chain=true
      AND i.block_timestamp >= $from
      AND i.block_timestamp <= $to;
      """

  def getStartEndTime()(
      implicit executionContext: ExecutionContext): DBActionR[Option[(TimeStamp, TimeStamp)]] = {
    val ft = finalizationTime
    getMinMaxInputsTs.flatMap(_.headOption match {
      //No input in db
      case None | Some((TimeStamp.zero, TimeStamp.zero)) => DBIOAction.successful(None)
      // inputs are only after finalization time, noop
      case Some((start, _)) if ft.isBefore(start) => DBIOAction.successful(None)
      case Some((start, _end)) =>
        val end = if (_end.isBefore(ft)) _end else ft
        getLastFinalizedInputTime().map {
          case None =>
            Some((start, end))
          case Some(lastFinalizedInputTime) =>
            Some((lastFinalizedInputTime, end))
        }
    })
  }

  private val getMinMaxInputsTs: DBActionSR[(TimeStamp, TimeStamp)] =
    sql"""
    SELECT MIN(block_timestamp),Max(block_timestamp) FROM inputs WHERE main_chain = true
    """.asAS[(TimeStamp, TimeStamp)]

  private def getLastFinalizedInputTime()(
      implicit executionContext: ExecutionContext): DBActionR[Option[TimeStamp]] =
    AppStateQueries.get(LastFinalizedInputTime).map(_.map(_.time))

  private def updateLastFinalizedInputTime(time: TimeStamp) =
    AppStateQueries.insertOrUpdate(LastFinalizedInputTime(time))
}
