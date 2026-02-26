// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.service

import java.time.{Instant, LocalTime, ZonedDateTime, ZoneOffset}

import scala.collection.immutable.ArraySeq
import scala.concurrent.{ExecutionContext, Future}

import com.typesafe.scalalogging.StrictLogging
import slick.basic.DatabaseConfig
import slick.jdbc.{PositionedParameters, PostgresProfile, SetParameter, SQLActionBuilder}
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.api.model.IntervalType
import org.alephium.explorer.persistence._
import org.alephium.explorer.persistence.DBRunner._
import org.alephium.explorer.persistence.queries.QuerySplitter
import org.alephium.explorer.persistence.schema.CustomGetResult._
import org.alephium.explorer.persistence.schema.CustomSetParameter._
import org.alephium.explorer.util.Scheduler
import org.alephium.explorer.util.SlickUtil._
import org.alephium.explorer.util.TimeUtil._
import org.alephium.protocol.ALPH
import org.alephium.util.TimeStamp

case object ActiveAddressHistoryService extends StrictLogging {

  def start(scheduleTime: LocalTime)(implicit
      executionContext: ExecutionContext,
      databaseConfig: DatabaseConfig[PostgresProfile],
      scheduler: Scheduler
  ): Future[Unit] = {
    Future.successful {
      scheduler.scheduleDailyAt(
        taskId = TokenSupplyService.productPrefix,
        at = ZonedDateTime
          .ofInstant(Instant.EPOCH, ZoneOffset.UTC)
          .plusSeconds(scheduleTime.toSecondOfDay().toLong)
      )(syncOnce())
    }
  }

  def syncOnce()(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[Unit] = {
    logger.debug("Updating transactions count")
    val startedAt = TimeStamp.now()
    update().map { _ =>
      val duration = TimeStamp.now().deltaUnsafe(startedAt)
      logger.debug(s"Active Address history updated in ${duration.millis} ms")
    }
  }

  private def update()(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[Unit] = {
    run(
      findLatestTransationTimestamp().flatMap {
        case None => DBIO.successful(())
        case Some(latestTxTs) =>
          for {
            _ <- updateDAA(latestTxTs)
            _ <- updateMAA(latestTxTs)
          } yield ()
      }
    )
  }

  private def updateDAA(
      latestTxTs: TimeStamp
  )(implicit ec: ExecutionContext): DBActionRW[Unit] = {
    findLatestHistoryTimestamp(IntervalType.Daily).flatMap { histTsOpt =>
      val start = histTsOpt
        .map { histTs =>
          stepBack(histTs, IntervalType.Daily)
        }
        .getOrElse(ALPH.LaunchTimestamp)

      val rangesTmp = getTimeRanges(start, latestTxTs, IntervalType.Daily)

      val ranges = (for {
        head <- rangesTmp.headOption
        last <- rangesTmp.lastOption
      } yield {
        Seq((head._1, last._2))
      }).getOrElse(Seq.empty)

      DBIO
        .sequence(
          ranges.map { range =>
            range match {
              case (from, to) =>
                computeDAA(from, to)
            }
          }
        )
        .map(_ => ())
    }
  }

  private def updateMAA(
      latestTxTs: TimeStamp
  )(implicit ec: ExecutionContext): DBActionRW[Unit] = {
    findLatestHistoryTimestamp(IntervalType.Monthly).flatMap { histTsOpt =>
      val start = histTsOpt
        .map { histTs =>
          stepBack(histTs, IntervalType.Monthly)
        }
        .getOrElse(ALPH.LaunchTimestamp)

      val rangesTmp = getTimeRanges(start, latestTxTs, IntervalType.Monthly)

      val ranges = (for {
        head <- rangesTmp.headOption
        last <- rangesTmp.lastOption
      } yield {
        Seq((head._1, last._2))
      }).getOrElse(Seq.empty)

      DBIO
        .sequence(
          ranges.map { range =>
            range match {
              case (from, to) =>
                computeMAA(from, to)
            }
          }
        )
        .map(_ => ())
    }
  }

  private def computeDAA(
      from: TimeStamp,
      to: TimeStamp
  )(implicit
      ec: ExecutionContext
  ): DBActionRW[Int] = {
    logger.debug("Starting computation of Daily Active Addresses (DAA)")
    computeDAAQuery(from, to).flatMap { results =>
      logger.debug(s"Computed ${results.size} daily active addresses")
      insertDAA(results) // Insert results into the database
    }
  }

  private def computeMAA(
      from: TimeStamp,
      to: TimeStamp
  )(implicit
      ec: ExecutionContext
  ): DBActionRW[Int] = {
    logger.debug("Starting computation of Monthly Active Addresses (MAA)")
    computeMAAQuery(from, to).flatMap { results =>
      logger.debug(s"Computed ${results.size} monthly active addresses")
      insertMAA(results) // Insert results into the database
    }
  }

  private def computeDAAQuery(
      from: TimeStamp,
      to: TimeStamp
  ): DBActionSR[(TimeStamp, Long)] = {
    sql"""
      WITH daily_active_addresses AS (
          SELECT
              (EXTRACT(EPOCH FROM DATE_TRUNC('day', to_timestamp(block_timestamp / 1000) AT TIME ZONE 'UTC')) * 1000)::BIGINT AS transaction_date_unix,
              address,
              COUNT(DISTINCT tx_hash) AS tx_count
          FROM
              transaction_per_addresses
          WHERE
              main_chain = true -- Only consider transactions in the main chain
          AND
            block_timestamp >= $from
          AND
            block_timestamp < $to
          GROUP BY
              transaction_date_unix, address
      )
      SELECT
          transaction_date_unix AS transaction_date,
          COUNT(DISTINCT address) AS daily_count
      FROM
          daily_active_addresses
      GROUP BY
          transaction_date_unix
      ORDER BY
          transaction_date_unix;

      """.asAS[(TimeStamp, Long)]
  }

  private def computeMAAQuery(
      from: TimeStamp,
      to: TimeStamp
  ): DBActionSR[(TimeStamp, Long)] = {
    sql"""
      WITH monthly_active_addresses AS (
          SELECT
              (EXTRACT(EPOCH FROM DATE_TRUNC('month', to_timestamp(block_timestamp / 1000) AT TIME ZONE 'UTC')) * 1000)::BIGINT AS transaction_date_unix,
              address,
              COUNT(DISTINCT tx_hash) AS tx_count
          FROM
              transaction_per_addresses
          WHERE
              main_chain = true -- Only consider transactions in the main chain
          AND
              block_timestamp >= $from
          AND
              block_timestamp < $to
          GROUP BY
              transaction_date_unix, address
      )
      SELECT
          transaction_date_unix AS transaction_date,
          COUNT(DISTINCT address) AS monthly_count
      FROM
          monthly_active_addresses
      GROUP BY
          transaction_date_unix
      ORDER BY
          transaction_date_unix;
    """.asAS[(TimeStamp, Long)]
  }

  private def insertData(
      values: Seq[(TimeStamp, Long)],
      intervalType: IntervalType
  ): DBActionW[Int] = {
    QuerySplitter.splitUpdates(rows = values, columnsPerRow = 3) { (rows, placeholders) =>
      val query =
        s"""
          INSERT INTO active_address_history(timestamp, value, interval_type)
          VALUES $placeholders
          ON CONFLICT (interval_type, timestamp) DO UPDATE
          SET value = EXCLUDED.value
        """

      val parameters: SetParameter[Unit] = (_, ps: PositionedParameters) =>
        rows.foreach { case (timestamp, count) =>
          ps >> timestamp
          ps >> count
          ps >> intervalType
        }

      SQLActionBuilder(query, parameters).asUpdate
    }
  }

  private def insertDAA(values: Seq[(TimeStamp, Long)]): DBActionW[Int] = {
    insertData(values, IntervalType.Daily)
  }

  private def insertMAA(values: Seq[(TimeStamp, Long)]): DBActionW[Int] = {
    insertData(values, IntervalType.Monthly)
  }

  private def findLatestHistoryTimestamp(
      intervalType: IntervalType
  )(implicit ec: ExecutionContext): DBActionR[Option[TimeStamp]] = {
    sql"""
      SELECT MAX(timestamp) FROM active_address_history
      WHERE interval_type = $intervalType
    """.asAS[Option[TimeStamp]].exactlyOne
  }

  private def findLatestTransationTimestamp()(implicit
      ec: ExecutionContext
  ): DBActionR[Option[TimeStamp]] = {
    sql"""
    SELECT MAX(block_timestamp) FROM latest_blocks
    """.asAS[Option[TimeStamp]].exactlyOne
  }

  def getActiveAddressesQuery(
      from: TimeStamp,
      to: TimeStamp,
      intervalType: IntervalType
  ): DBActionSR[(TimeStamp, Long)] = {
    sql"""
      SELECT timestamp, value FROM active_address_history
      WHERE interval_type = ${intervalType}
      AND timestamp >= $from
      AND timestamp <= $to
      ORDER BY timestamp
    """.asAS[(TimeStamp, Long)]
  }

  def getActiveAddresses(
      from: TimeStamp,
      to: TimeStamp,
      intervalType: IntervalType
  )(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[(TimeStamp, Long)]] = {
    intervalType match {
      case IntervalType.Daily | IntervalType.Monthly =>
        run(getActiveAddressesQuery(from, to, intervalType)).map(ArraySeq.from(_))
      case _ =>
        Future.failed(new IllegalArgumentException("Unsupported interval type"))
    }
  }
}
