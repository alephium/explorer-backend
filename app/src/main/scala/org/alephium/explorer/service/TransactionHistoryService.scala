// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

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
import org.alephium.protocol.model.{GroupIndex, TransactionId}
import org.alephium.util.{Duration, TimeStamp}

case object TransactionHistoryService extends StrictLogging {

  val hourlyStepBack: Duration = Duration.ofHoursUnsafe(2)
  val dailyStepBack: Duration  = Duration.ofDaysUnsafe(1)
  val weeklyStepBack: Duration = Duration.ofDaysUnsafe(7)

  private type FetchedData = (TimeStamp, Int, Int, Long)

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
  ): Future[ArraySeq[PerChainTimedCount]] = {
    run(getPerChainQuery(intervalType, from, to)).map(res =>
      ArraySeq
        .unsafeWrapArray(
          res
            .groupBy { case (timestamp, _, _, _) =>
              timestamp
            }
            .map { case (timestamp, values) =>
              val perChainCounts = values.map { case (_, chainFrom, chainTo, count) =>
                PerChainCount(chainFrom.value, chainTo.value, count)
              }
              PerChainTimedCount(timestamp, perChainCounts)
            }
            .toArray
        )
        .sortBy(_.timestamp)
    ) // We need to sort because `groupBy` doesn't preserve order
  }

  def getAllChains(from: TimeStamp, to: TimeStamp, intervalType: IntervalType)(implicit
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[(TimeStamp, Long)]] = {
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
      fetchData: (TimeStamp, TimeStamp) => DBActionSR[FetchedData]
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
      data: Seq[FetchedData],
      ranges: Seq[(TimeStamp, TimeStamp)],
      intervalType: IntervalType
  )(implicit gs: GroupSetting): Seq[(TimeStamp, Int, Int, Long, IntervalType)] = {
    ranges.flatMap { case (from, to) =>
      val perChainCount = gs.chainIndexes.map { chainIndex =>
        (
          chainIndex.from.value,
          chainIndex.to.value,
          data
            .filter { case (timestamp, chainFrom, chainTo, _) =>
              timestamp >= from &&
              timestamp <= to &&
              chainFrom == chainIndex.from.value &&
              chainTo == chainIndex.to.value
            }
            .map { case (_, _, _, count) => count }
            .sum
        )
      }
      val allCount = (-1, -1, perChainCount.map { case (_, _, count) => count }.sum)

      (perChainCount :+ allCount).map { case (chainFrom, chainTo, count) =>
        (from, chainFrom, chainTo, count, intervalType)
      }
    }
  }

  // WE need to get to substract the number of conflicted transactions
  private def getTxsCountFromTo(
      from: TimeStamp,
      to: TimeStamp
  )(implicit ec: ExecutionContext): DBActionSR[FetchedData] = {
    sql"""
      SELECT block_timestamp, chain_from, chain_to, txs_count, conflicted_txs
      FROM block_headers
      WHERE block_timestamp >= $from
      AND block_timestamp <= $to
      AND main_chain = true
    """
      .asAS[(TimeStamp, Int, Int, Long, Option[ArraySeq[TransactionId]])]
      .map(_.map { case (timestamp, chainFrom, chainTo, txsCount, conflictedTxs) =>
        (timestamp, chainFrom, chainTo, txsCount - conflictedTxs.map(_.size.toLong).getOrElse(0L))
      })
  }

  private def getHourlyCountFromTo(
      from: TimeStamp,
      to: TimeStamp
  ): DBActionSR[FetchedData] = {
    sql"""
      SELECT timestamp, chain_from, chain_to, value
      FROM transactions_history
      WHERE timestamp >= $from
      AND timestamp <= $to
      AND interval_type = ${IntervalType.Hourly}
    """.asAS[FetchedData]
  }

  private def insertValues(
      values: Iterable[(TimeStamp, Int, Int, Long, IntervalType)]
  ): DBActionW[Int] =
    QuerySplitter.splitUpdates(rows = values, columnsPerRow = 5) { (values, placeholder) =>
      val query =
        s"""
          INSERT INTO transactions_history(timestamp, chain_from, chain_to, value, interval_type)
          VALUES $placeholder
          ON CONFLICT (interval_type, timestamp, chain_from, chain_to) DO UPDATE
          SET value = EXCLUDED.value
        """

      val parameters: SetParameter[Unit] =
        (_: Unit, params: PositionedParameters) =>
          values foreach { case (from, chainFrom, chainTo, count, intervalType) =>
            params >> from
            params >> chainFrom
            params >> chainTo
            params >> count
            params >> intervalType
          }

      SQLActionBuilder(
        sql = query,
        setParameter = parameters
      ).asUpdate
    }

  /*
   * Step back a bit in time to recompute some latest values,
   * to be sure we didn't miss some unsynced blocks
   */
  private def stepBack(timestamp: TimeStamp, intervalType: IntervalType): TimeStamp =
    intervalType match {
      case IntervalType.Hourly  => timestamp.minusUnsafe(hourlyStepBack)
      case IntervalType.Daily   => timestamp.minusUnsafe(dailyStepBack)
      case IntervalType.Weekly  => timestamp.minusUnsafe(weeklyStepBack)
      case IntervalType.Monthly => timestamp.minusUnsafe(monthlyStepBack)
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
  ): DBActionSR[(TimeStamp, GroupIndex, GroupIndex, Long)] = {

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

  def getAllChainsQuery(
      intervalType: IntervalType,
      from: TimeStamp,
      to: TimeStamp
  ): DBActionSR[(TimeStamp, Long)] = {
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
