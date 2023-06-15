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
import scala.concurrent.duration.{Duration => ScalaDuration, FiniteDuration}

import com.typesafe.scalalogging.StrictLogging
import slick.basic.DatabaseConfig
import slick.dbio.DBIO
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.GroupSetting
import org.alephium.explorer.api.model._
import org.alephium.explorer.foldFutures
import org.alephium.explorer.persistence._
import org.alephium.explorer.persistence.DBRunner._
import org.alephium.explorer.persistence.schema._
import org.alephium.explorer.persistence.schema.CustomGetResult._
import org.alephium.explorer.persistence.schema.CustomJdbcTypes._
import org.alephium.explorer.persistence.schema.CustomSetParameter._
import org.alephium.explorer.util.Scheduler
import org.alephium.explorer.util.SlickUtil._
import org.alephium.explorer.util.TimeUtil._
import org.alephium.protocol.ALPH
import org.alephium.protocol.model.GroupIndex
import org.alephium.util.{Duration, TimeStamp}

case object TransactionHistoryService extends StrictLogging {

  val hourlyStepBack: Duration = Duration.ofHoursUnsafe(2)
  val dailyStepBack: Duration  = Duration.ofDaysUnsafe(1)

  def start(interval: FiniteDuration)(implicit executionContext: ExecutionContext,
                                      databaseConfig: DatabaseConfig[PostgresProfile],
                                      scheduler: Scheduler,
                                      gs: GroupSetting): Future[Unit] =
    scheduler.scheduleLoop(
      taskId        = TransactionHistoryService.productPrefix,
      firstInterval = ScalaDuration.Zero,
      loopInterval  = interval,
      stop          = None
    )(syncOnce())

  def syncOnce()(implicit ec: ExecutionContext,
                 dc: DatabaseConfig[PostgresProfile],
                 gs: GroupSetting): Future[Unit] = {
    logger.debug("Updating transactions count")
    val startedAt = TimeStamp.now()
    updateTxHistoryCount().map { _ =>
      val duration = TimeStamp.now().deltaUnsafe(startedAt)
      logger.debug(s"Transactions history updated in ${duration.millis} ms")
    }
  }

  def getPerChain(from: TimeStamp, to: TimeStamp, intervalType: IntervalType)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[ArraySeq[PerChainTimedCount]] = {
    run(getPerChainQuery(intervalType, from, to)).map(
      res =>
        ArraySeq
          .unsafeWrapArray(res
            .groupBy {
              case (timestamp, _, _, _) => timestamp
            }
            .map {
              case (timestamp, values) =>
                val perChainCounts = values.map {
                  case (_, chainFrom, chainTo, count) =>
                    PerChainCount(chainFrom.value, chainTo.value, count)
                }
                PerChainTimedCount(timestamp, perChainCounts)
            }
            .toArray)
          .sortBy(_.timestamp)) //We need to sort because `groupBy` doesn't preserve order
  }

  def getAllChains(from: TimeStamp, to: TimeStamp, intervalType: IntervalType)(
      implicit dc: DatabaseConfig[PostgresProfile]): Future[ArraySeq[(TimeStamp, Long)]] = {
    run(
      getAllChainsQuery(intervalType, from, to)
    )
  }

  private def updateTxHistoryCount()(implicit ec: ExecutionContext,
                                     dc: DatabaseConfig[PostgresProfile],
                                     gs: GroupSetting): Future[Unit] = {
    findLatestTransationTimestamp().flatMap {
      case None => Future.successful(()) //noop
      case Some(latestTxTs) =>
        for {
          _ <- updateTxHistoryCountForInterval(latestTxTs, IntervalType.Daily)
          _ <- updateTxHistoryCountForInterval(latestTxTs, IntervalType.Hourly)
        } yield ()
    }
  }

  def updateTxHistoryCountForInterval(latestTxTs: TimeStamp, intervalType: IntervalType)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      gs: GroupSetting): Future[Unit] = {
    run(findLatestHistoryTimestamp(intervalType)).flatMap { histTsOpt =>
      val start = histTsOpt
        .map { histTs =>
          stepBack(histTs, intervalType)
        }
        .getOrElse(ALPH.LaunchTimestamp)

      val ranges = getTimeRanges(start, latestTxTs, intervalType)

      foldFutures(ranges) {
        case (from, to) =>
          run(
            DBIO
              .sequence(
                gs.chainIndexes.map { chainIndex =>
                  countAndInsertPerChain(intervalType, from, to, chainIndex.from, chainIndex.to)
                } :+ countAndInsertAllChains(intervalType, from, to)
              )
              .transactionally
          )
      }.map(_ => ())
    }
  }

  private def truncate(timestamp: TimeStamp, intervalType: IntervalType): TimeStamp =
    intervalType match {
      case IntervalType.Daily  => truncatedToDay(timestamp)
      case IntervalType.Hourly => truncatedToHour(timestamp)
    }

  @SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
  def getTimeRanges(histTs: TimeStamp,
                    latestTxTs: TimeStamp,
                    intervalType: IntervalType): ArraySeq[(TimeStamp, TimeStamp)] = {

    val oneMillis = Duration.ofMillisUnsafe(1)
    val start     = truncate(histTs, intervalType)
    val end       = truncate(latestTxTs, intervalType).minusUnsafe(oneMillis)

    if (start == end || end.isBefore(start)) {
      ArraySeq.empty
    } else {
      val step = (intervalType match {
        case IntervalType.Daily  => Duration.ofDaysUnsafe(1)
        case IntervalType.Hourly => Duration.ofHoursUnsafe(1)
      }).-(oneMillis).get

      buildTimestampRange(start, end, step)
    }
  }

  /*
   * Step back a bit in time to recompute some latest values,
   * to be sure we didn't miss some unsynced blocks
   */
  private def stepBack(timestamp: TimeStamp, intervalType: IntervalType): TimeStamp =
    intervalType match {
      case IntervalType.Daily  => timestamp.minusUnsafe(dailyStepBack)
      case IntervalType.Hourly => timestamp.minusUnsafe(hourlyStepBack)
    }

  //TODO Replace by accessing a `Latest Transaction cache`
  private def findLatestTransationTimestamp()(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[Option[TimeStamp]] = {
    run(sql"""
    SELECT MAX(block_timestamp) FROM transactions WHERE main_chain = true
    """.asAS[Option[TimeStamp]].exactlyOne)
  }

  private def findLatestHistoryTimestamp(intervalType: IntervalType)(
      implicit ec: ExecutionContext): DBActionR[Option[TimeStamp]] = {
    TransactionHistorySchema.table
      .filter(_.intervalType === intervalType)
      .sortBy(_.timestamp.desc)
      .result
      .headOption
      .map(_.map(_.timestamp))
  }

  private def countAndInsertPerChain(intervalType: IntervalType,
                                     from: TimeStamp,
                                     to: TimeStamp,
                                     chainFrom: GroupIndex,
                                     chainTo: GroupIndex) = {
    sqlu"""
      INSERT INTO transactions_history(timestamp, chain_from, chain_to, value, interval_type)
        SELECT $from, $chainFrom, $chainTo, COUNT(*), $intervalType
        FROM transactions
        WHERE main_chain = true
        AND block_timestamp >= $from
        AND block_timestamp <= $to
        AND chain_from = $chainFrom
        and chain_to = $chainTo
        ON CONFLICT (interval_type, timestamp, chain_from, chain_to) DO UPDATE
        SET value = EXCLUDED.value
    """
  }

  //count all chains tx and insert with special group index -1
  private def countAndInsertAllChains(intervalType: IntervalType,
                                      from: TimeStamp,
                                      to: TimeStamp) = {
    sqlu"""
      INSERT INTO transactions_history(timestamp, chain_from, chain_to, value, interval_type)
        SELECT $from, -1, -1, COUNT(*), $intervalType
        FROM transactions
        WHERE main_chain = true
        AND block_timestamp >= $from
        AND block_timestamp <= $to
        ON CONFLICT (interval_type, timestamp, chain_from, chain_to) DO UPDATE
        SET value = EXCLUDED.value
    """
  }

  def getPerChainQuery(intervalType: IntervalType,
                       from: TimeStamp,
                       to: TimeStamp): DBActionSR[(TimeStamp, GroupIndex, GroupIndex, Long)] = {

    sql"""
        SELECT timestamp, chain_from, chain_to, value
        FROM transactions_history
        WHERE interval_type = $intervalType
        AND timestamp >= $from
        AND timestamp <= $to
        AND chain_from <> -1
        AND chain_to <> -1
        ORDER BY timestamp
      """.asAS[(TimeStamp, GroupIndex, GroupIndex, Long)]
  }

  def getAllChainsQuery(intervalType: IntervalType,
                        from: TimeStamp,
                        to: TimeStamp): DBActionSR[(TimeStamp, Long)] = {
    sql"""
        SELECT timestamp, value FROM transactions_history
        WHERE interval_type = $intervalType
        AND timestamp >= $from
        AND timestamp <= $to
        AND chain_from = -1
        AND chain_to = -1
        ORDER BY timestamp
      """.asAS[(TimeStamp, Long)]
  }
}
