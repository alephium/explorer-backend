// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.service

import scala.collection.immutable.ArraySeq
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import com.typesafe.scalalogging.StrictLogging
import slick.basic.DatabaseConfig
import org.alephium.explorer.persistence._
import slick.jdbc.{PositionedParameters, PostgresProfile, SetParameter, SQLActionBuilder}
import slick.jdbc.PostgresProfile.api._
import org.alephium.explorer.persistence.queries.QuerySplitter
import org.alephium.explorer.api.model.IntervalType
import org.alephium.explorer.persistence.schema.CustomGetResult._
import org.alephium.explorer.persistence.schema.CustomSetParameter._
import org.alephium.explorer.persistence.DBRunner._
import org.alephium.explorer.util.Scheduler
import org.alephium.explorer.util.TimeUtil._
import org.alephium.protocol.ALPH
import org.alephium.util.TimeStamp
import org.alephium.explorer.util.SlickUtil._

case object ActiveAddressHistoryService extends StrictLogging {

  def start()(implicit
      executionContext: ExecutionContext,
      databaseConfig: DatabaseConfig[PostgresProfile],
      scheduler: Scheduler
  ): Future[Unit] = {
    syncOnce()
  }

  def syncOnce()(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
  ): Future[Unit] = {
    println(s"${Console.RED}${Console.BOLD}*** syncOnce ***${Console.RESET}")
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
        println(s"${Console.RED}${Console.BOLD}*** latestTxTs ***${Console.RESET}${latestTxTs}")
        for {
          _ <- updateDAA(latestTxTs)
        } yield ()
    }
    )

  }

  private def updateDAA(latestTxTs:TimeStamp)(implicit ec:ExecutionContext, dc: DatabaseConfig[PostgresProfile]):DBActionRW[Unit] = {
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
        println(s"${Console.RED}${Console.BOLD}*** head ***${Console.RESET}${head}")
        println(s"${Console.RED}${Console.BOLD}*** last ***${Console.RESET}${last}")
        Seq((head._1, last._2))
      }).getOrElse(Seq.empty)


      DBIO.sequence(
        ranges.map { range =>
          println(s"${Console.RED}${Console.BOLD}*** range ***${Console.RESET}${range}")
          range match {case (from, to) =>
          computeDAA(from, to)
          }
        }
      ).map(_ => ())
    }
  }


  private def computeDAA(
    from:TimeStamp,
  to: TimeStamp
  )(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): DBActionRW[Int] = {
    println(s"${Console.RED}${Console.BOLD}*** computeDAA ***${Console.RESET}")
    println(s"${Console.RED}${Console.BOLD}*** from ***${Console.RESET}${from}")
  println(s"${Console.RED}${Console.BOLD}*** to ***${Console.RESET}${to}")
    logger.debug("Starting computation of Daily Active Addresses (DAA)")
    getDailyActiveAddressesQuery(from,to).flatMap { results =>
      logger.debug(s"Computed ${results.size} daily active addresses")
      insertDAA(results) // Insert results into the database
    }
  }

  private def computeMAA(
    from:TimeStamp,
  to: TimeStamp
    )(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[Int] = {
    logger.debug("Starting computation of Monthly Active Addresses (MAA)")
    run(getMonthlyActiveAddressesQuery(from,to)).flatMap { results =>
      logger.debug(s"Computed ${results.size} monthly active addresses")
      run(insertMAA(results)) // Insert results into the database
    }
  }

  private def getDailyActiveAddressesQuery(
      from: TimeStamp,
      to: TimeStamp
    )
    (implicit
      ec: ExecutionContext,
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

      """.asAS[(TimeStamp,Long)]
  }

  private def getMonthlyActiveAddressesQuery(
      from: TimeStamp,
      to: TimeStamp
  ): DBActionSR[(TimeStamp, Long)] = {
    sql"""
      WITH monthly_active_addresses AS (
        SELECT
          date_trunc('month', to_timestamp(block_timestamp / 1000))::date AS transaction_month,
          address
        FROM
          transaction_per_addresses
        WHERE
          main_chain = true
        AND
          block_timestamp >= $from
        AND
          block_timestamp < $to
        GROUP BY
          transaction_month, address
      )
      SELECT
        transaction_month,
        COUNT(DISTINCT address) AS monthly_count
      FROM
        monthly_active_addresses
      GROUP BY
        transaction_month
      ORDER BY
        transaction_month
    """.asAS[(TimeStamp, Long)]
  }

  private def insertDAA(values: Seq[(TimeStamp, Long)]): DBActionW[Int] = {
    QuerySplitter.splitUpdates(rows = values, columnsPerRow = 3) { (rows, placeholders) =>
      val query =
        s"""
          INSERT INTO active_address_history(timestamp, value, interval_type)
          VALUES $placeholders
          ON CONFLICT (interval_type, timestamp) DO UPDATE
          SET value = EXCLUDED.value
        """

      val parameters: SetParameter[Unit] = (_, ps: PositionedParameters) =>
        rows.foreach { case (transactionDate, dailyCount) =>
          ps >> transactionDate
          ps >> dailyCount
          ps >> IntervalType.Daily
        }

      SQLActionBuilder(query, parameters).asUpdate
    }
  }

  private def insertMAA(values: Seq[(TimeStamp, Long)]): DBActionW[Int] = {
    QuerySplitter.splitUpdates(rows = values, columnsPerRow = 2) { (rows, placeholders) =>
      val query =
        s"""
          INSERT INTO monthly_active_addresses(transaction_month, monthly_count)
          VALUES $placeholders
          ON CONFLICT (transaction_month) DO UPDATE SET monthly_count = EXCLUDED.monthly_count
        """

      val parameters: SetParameter[Unit] = (_, ps: PositionedParameters) =>
        rows.foreach { case (transactionMonth, monthlyCount) =>
          ps >> transactionMonth
          ps >> monthlyCount
        }

      SQLActionBuilder(query, parameters).asUpdate
    }
  }

  // Calculate the delay until the given hour (e.g., 1 AM)
  private def computeDelay(hour: Int): FiniteDuration = {
    import java.time.{Duration, LocalDateTime}
    val now = LocalDateTime.now()
    val nextRun =
      if (now.getHour < hour) now.withHour(hour).withMinute(0).withSecond(0).withNano(0)
      else now.plusDays(1).withHour(hour).withMinute(0).withSecond(0).withNano(0)
    Duration.between(now, nextRun).toMillis.millis
  }

  // Calculate the delay until the first day of next month at the given hour (e.g., 1 AM)
  private def computeFirstDayDelay(hour: Int): FiniteDuration = {
    import java.time.{Duration, LocalDateTime}
    val now = LocalDateTime.now()
    val nextRun = now.plusMonths(1).withDayOfMonth(1).withHour(hour).withMinute(0).withSecond(0).withNano(0)
    Duration.between(now, nextRun).toMillis.millis
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
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): DBActionR[Option[TimeStamp]] = {
    sql"""
    SELECT MAX(block_timestamp) FROM latest_blocks
    """.asAS[Option[TimeStamp]].exactlyOne
  }

}
