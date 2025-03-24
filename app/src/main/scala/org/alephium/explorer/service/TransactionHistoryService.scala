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
import slick.jdbc.{PositionedParameters, PostgresProfile, SetParameter, SQLActionBuilder}
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.GroupSetting
import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence._
import org.alephium.explorer.persistence.DBRunner._
import org.alephium.explorer.persistence.queries.QuerySplitter
import org.alephium.explorer.persistence.schema.CustomGetResult._
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
  val weeklyStepBack: Duration = Duration.ofDaysUnsafe(7)

  def start(interval: FiniteDuration)(implicit
      executionContext: ExecutionContext,
      databaseConfig: DatabaseConfig[PostgresProfile],
      scheduler: Scheduler,
      gs: GroupSetting
  ): Future[Unit] =
    scheduler.scheduleLoop(
      taskId = TransactionHistoryService.productPrefix,
      firstInterval = ScalaDuration.Zero,
      loopInterval = interval
    )(syncOnce())

  def syncOnce()(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      gs: GroupSetting
  ): Future[Unit] = {
    logger.debug("Updating transactions count")
    val startedAt = TimeStamp.now()
    updateTxHistoryCount().map { _ =>
      val duration = TimeStamp.now().deltaUnsafe(startedAt)
      logger.debug(s"Transactions history updated in ${duration.millis} ms")
    }
  }

  def getPerChain(from: TimeStamp, to: TimeStamp, intervalType: IntervalType)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[PerChainTimedTxCount]] = {
    run(getPerChainQuery(intervalType, from, to)).map(res =>
      ArraySeq
        .unsafeWrapArray(
          res
            .groupBy { case (timestamp, _, _, _, _) =>
              timestamp
            }
            .map { case (timestamp, values) =>
              val perChainCounts =
                values.map { case (_, chainFrom, chainTo, count, nonCoinbaseCount) =>
                  PerChainTxCount(chainFrom.value, chainTo.value, count, nonCoinbaseCount)
                }
              PerChainTimedTxCount(timestamp, perChainCounts)
            }
            .toArray
        )
        .sortBy(_.timestamp)
    ) // We need to sort because `groupBy` doesn't preserve order
  }

  def getAllChains(from: TimeStamp, to: TimeStamp, intervalType: IntervalType)(implicit
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[(TimeStamp, Long, Option[Long])]] = {
    run(
      getAllChainsQuery(intervalType, from, to)
    )
  }

  private def updateTxHistoryCount()(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      gs: GroupSetting
  ): Future[Unit] = {
    findLatestTransationTimestamp().flatMap {
      case None => Future.successful(()) // noop
      case Some(latestTxTs) =>
        for {
          _ <- updateHourlyTxHistoryCount(latestTxTs)
          _ <- updateDailyTxHistoryCount(latestTxTs)
        } yield ()
    }
  }

  def updateHourlyTxHistoryCount(latestTxTs: TimeStamp)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      gs: GroupSetting
  ): Future[Unit] = {
    updateTxHistoryCountForInterval(IntervalType.Hourly, latestTxTs, getTxsCountFromTo)
  }

  def updateDailyTxHistoryCount(latestTxTs: TimeStamp)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      gs: GroupSetting
  ): Future[Unit] = {
    updateTxHistoryCountForInterval(IntervalType.Daily, latestTxTs, getHourlyCountFromTo)
  }

  private def updateTxHistoryCountForInterval(
      intervalType: IntervalType,
      latestTxTs: TimeStamp,
      fetchData: (TimeStamp, TimeStamp) => DBActionSR[(TimeStamp, Int, Int, Long, Option[Long])]
  )(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      gs: GroupSetting
  ): Future[Unit] = {
    run(findLatestHistoryTimestamp(intervalType)).flatMap { histTsOpt =>
      val start = histTsOpt
        .map { histTs =>
          stepBack(histTs, intervalType)
        }
        .getOrElse(ALPH.LaunchTimestamp)

      val ranges = getTimeRanges(start, latestTxTs, intervalType)
      val allTs  = ranges.flatMap { case (from, to) => Seq(from, to) }

      (for {
        minTs <- allTs.minOption
        maxTs <- allTs.maxOption
      } yield (minTs, maxTs)) match {
        case Some((minTs, maxTs)) =>
          run(fetchData(minTs, maxTs)).flatMap { data =>
            val processedData = processDataForTimeRanges(data, ranges, intervalType)
            run(insertValues(processedData)).map(_ => ())
          }
        case None => Future.successful(())
      }
    }
  }

  private def processDataForTimeRanges(
      data: Seq[(TimeStamp, Int, Int, Long, Option[Long])],
      ranges: Seq[(TimeStamp, TimeStamp)],
      intervalType: IntervalType
  )(implicit gs: GroupSetting): Seq[(TimeStamp, Int, Int, Long, Long, IntervalType)] = {
    ranges.flatMap { case (from, to) =>
      val perChainCount = gs.chainIndexes.map { chainIndex =>
        val (count, nonCoinbaseCount) = data
          .filter { case (timestamp, chainFrom, chainTo, _, _) =>
            timestamp >= from &&
            timestamp <= to &&
            chainFrom == chainIndex.from.value &&
            chainTo == chainIndex.to.value
          }
          .foldLeft((0L, 0L)) {
            case ((currentCount, currentNonCoinbaseCount), (_, _, _, count, nonCoinbaseCount)) =>
              (currentCount + count, currentNonCoinbaseCount + nonCoinbaseCount.getOrElse(0L))
          }

        (
          chainIndex.from.value,
          chainIndex.to.value,
          count,
          nonCoinbaseCount
        )
      }

      val (count, nonCoinbaseCount) = perChainCount.foldLeft((0L, 0L)) {
        case ((currentCount, currentNonCoinbaseCount), (_, _, count, nonCoinbaseCount)) =>
          (currentCount + count, currentNonCoinbaseCount + nonCoinbaseCount)
      }

      val allCount = (-1, -1, count, nonCoinbaseCount)

      (perChainCount :+ allCount).map { case (chainFrom, chainTo, count, nonCoinbaseCount) =>
        (from, chainFrom, chainTo, count, nonCoinbaseCount, intervalType)
      }
    }
  }

  private def getTxsCountFromTo(
      from: TimeStamp,
      to: TimeStamp
  )(implicit ec: ExecutionContext): DBActionSR[(TimeStamp, Int, Int, Long, Option[Long])] = {
    sql"""
      SELECT block_timestamp, chain_from, chain_to, txs_count
      FROM block_headers
      WHERE block_timestamp >= $from
      AND block_timestamp <= $to
      AND main_chain = true
    """
      .asAS[(TimeStamp, Int, Int, Long)]
      .map(_.map { case (ts, from, to, value) =>
        (ts, from, to, value, Some(value - 1))
      })
  }

  private def getHourlyCountFromTo(
      from: TimeStamp,
      to: TimeStamp
  ): DBActionSR[(TimeStamp, Int, Int, Long, Option[Long])] = {
    sql"""
      SELECT timestamp, chain_from, chain_to, value, non_coinbase_value
      FROM transactions_history
      WHERE timestamp >= $from
      AND timestamp <= $to
      AND interval_type = ${IntervalType.Hourly}
    """.asAS[(TimeStamp, Int, Int, Long, Option[Long])]
  }

  private def insertValues(
      values: Iterable[(TimeStamp, Int, Int, Long, Long, IntervalType)]
  ): DBActionW[Int] =
    QuerySplitter.splitUpdates(rows = values, columnsPerRow = 6) { (values, placeholder) =>
      val query =
        s"""
          INSERT INTO transactions_history(timestamp, chain_from, chain_to, value, non_coinbase_value, interval_type)
          VALUES $placeholder
          ON CONFLICT (interval_type, timestamp, chain_from, chain_to) DO UPDATE
          SET value = EXCLUDED.value
        """

      val parameters: SetParameter[Unit] =
        (_: Unit, params: PositionedParameters) =>
          values foreach { case (from, chainFrom, chainTo, count, nonCoinbaseCount, intervalType) =>
            params >> from
            params >> chainFrom
            params >> chainTo
            params >> count
            params >> nonCoinbaseCount
            params >> intervalType
          }

      SQLActionBuilder(
        sql = query,
        setParameter = parameters
      ).asUpdate
    }

  private def truncate(timestamp: TimeStamp, intervalType: IntervalType): TimeStamp =
    intervalType match {
      case IntervalType.Hourly => truncatedToHour(timestamp)
      case IntervalType.Daily  => truncatedToDay(timestamp)
      case IntervalType.Weekly => truncatedToWeek(timestamp)
    }

  @SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
  def getTimeRanges(
      histTs: TimeStamp,
      latestTxTs: TimeStamp,
      intervalType: IntervalType
  ): ArraySeq[(TimeStamp, TimeStamp)] = {

    val oneMillis = Duration.ofMillisUnsafe(1)
    val start     = truncate(histTs, intervalType)
    val end       = truncate(latestTxTs, intervalType).minusUnsafe(oneMillis)

    if (start == end || end.isBefore(start)) {
      ArraySeq.empty
    } else {
      val step = (intervalType match {
        case IntervalType.Hourly => Duration.ofHoursUnsafe(1)
        case IntervalType.Daily  => Duration.ofDaysUnsafe(1)
        case IntervalType.Weekly => Duration.ofDaysUnsafe(7)
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
      case IntervalType.Hourly => timestamp.minusUnsafe(hourlyStepBack)
      case IntervalType.Daily  => timestamp.minusUnsafe(dailyStepBack)
      case IntervalType.Weekly => timestamp.minusUnsafe(weeklyStepBack)
    }

  private def findLatestTransationTimestamp()(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[Option[TimeStamp]] = {
    run(sql"""
    SELECT MAX(block_timestamp) FROM latest_blocks
    """.asAS[Option[TimeStamp]].exactlyOne)
  }

  private def findLatestHistoryTimestamp(
      intervalType: IntervalType
  )(implicit ec: ExecutionContext): DBActionR[Option[TimeStamp]] = {
    sql"""
      SELECT MAX(timestamp) FROM transactions_history
      WHERE interval_type = $intervalType
    """.asAS[Option[TimeStamp]].exactlyOne
  }

  def getPerChainQuery(
      intervalType: IntervalType,
      from: TimeStamp,
      to: TimeStamp
  ): DBActionSR[(TimeStamp, GroupIndex, GroupIndex, Long, Option[Long])] = {

    sql"""
        SELECT timestamp, chain_from, chain_to, value, non_coinbase_value
        FROM transactions_history
        WHERE interval_type = $intervalType
        AND timestamp >= $from
        AND timestamp <= $to
        AND chain_from <> -1
        AND chain_to <> -1
        ORDER BY timestamp
      """.asAS[(TimeStamp, GroupIndex, GroupIndex, Long, Option[Long])]
  }

  def getAllChainsQuery(
      intervalType: IntervalType,
      from: TimeStamp,
      to: TimeStamp
  ): DBActionSR[(TimeStamp, Long, Option[Long])] = {
    sql"""
        SELECT timestamp, value, non_coinbase_value FROM transactions_history
        WHERE interval_type = $intervalType
        AND timestamp >= $from
        AND timestamp <= $to
        AND chain_from = -1
        AND chain_to = -1
        ORDER BY timestamp
      """.asAS[(TimeStamp, Long, Option[Long])]
  }
}
