// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.service

import java.time.{Instant, LocalTime, ZonedDateTime, ZoneOffset}

import scala.collection.immutable.ArraySeq
import scala.concurrent.{ExecutionContext, Future}

import com.typesafe.scalalogging.StrictLogging
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence._
import org.alephium.explorer.persistence.DBRunner._
import org.alephium.explorer.persistence.model.AppState._
import org.alephium.explorer.persistence.queries.AppStateQueries
import org.alephium.explorer.persistence.queries.InfoQueries
import org.alephium.explorer.persistence.schema.CustomGetResult._
import org.alephium.explorer.persistence.schema.CustomSetParameter._
import org.alephium.explorer.util.Scheduler
import org.alephium.protocol.model.TokenId
import org.alephium.util.TimeStamp

trait HolderService {
  def getAlphHolders(pagination: Pagination)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[HolderInfo]]
  def getTokenHolders(token: TokenId, pagination: Pagination)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[HolderInfo]]
}

case object HolderService extends HolderService with StrictLogging {

  def getAlphHolders(pagination: Pagination)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[HolderInfo]] = {
    run(InfoQueries.getAlphHoldersAction(pagination)).map(_.map { case (address, balance) =>
      HolderInfo(address, balance)
    })
  }

  def getTokenHolders(token: TokenId, pagination: Pagination)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[HolderInfo]] = {
    run(InfoQueries.getTokenHoldersAction(token, pagination)).map(_.map { case (address, balance) =>
      HolderInfo(address, balance)
    })
  }

  def start(scheduleTime: LocalTime)(implicit
      executionContext: ExecutionContext,
      databaseConfig: DatabaseConfig[PostgresProfile],
      scheduler: Scheduler
  ): Future[Unit] = {
    Future.successful {
      scheduler.scheduleDailyAt(
        taskId = HolderService.productPrefix,
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
    logger.debug("Handle rich list")
    sync().map { _ =>
      logger.debug("Rich list updated")
    }
  }

  def sync()(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[Unit] = {
    run(getLatestTimestamps()).flatMap {
      case (None, None) =>
        Future.unit // noop
      case (Some(lastFinalizedInputTime), None) =>
        insertInitialHolders(lastFinalizedInputTime)
      case (Some(lastFinalizedInputTime), Some(lastRichListUpdate))
          if lastRichListUpdate < lastFinalizedInputTime =>
        updateHolders(lastRichListUpdate, lastFinalizedInputTime)
      case _ =>
        Future.unit // noop
    }
  }

  def getLatestTimestamps()(implicit
      ec: ExecutionContext
  ): DBActionR[(Option[TimeStamp], Option[TimeStamp])] = {
    for {
      lastFinalizedInputTime <- AppStateQueries.get(LastFinalizedInputTime).map(_.map(_.time))
      lastComputedBalance    <- AppStateQueries.get(LastHoldersUpdate).map(_.map(_.time))
    } yield (lastFinalizedInputTime, lastComputedBalance)
  }

  def insertInitialHolders(lastFinalizedInputTime: TimeStamp)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[Unit] = {
    run(for {
      _ <- insertInitialAlphHolders(lastFinalizedInputTime)
      _ <- insertInitialTokenHolders(lastFinalizedInputTime)
      _ <- AppStateQueries.insertOrUpdate(LastHoldersUpdate(lastFinalizedInputTime))
    } yield ())
  }

  def updateHolders(lastRichListUpdate: TimeStamp, lastFinalizedInputTime: TimeStamp)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[Unit] = run(
    for {
      _ <- updateAlphHolders(lastRichListUpdate, lastFinalizedInputTime)
      _ <- updateTokenHolders(lastRichListUpdate, lastFinalizedInputTime)
      _ <- AppStateQueries.insertOrUpdate(LastHoldersUpdate(lastFinalizedInputTime))
    } yield ()
  )

  def insertInitialAlphHolders(time: TimeStamp): DBActionW[Int] =
    sqlu"""
    INSERT INTO alph_holders (address, balance)
    SELECT outputs.address,
           COALESCE(SUM(outputs.amount), 0) AS total_balance
    FROM outputs
    WHERE
        outputs.block_timestamp <= $time
        AND (outputs.spent_finalized IS NULL OR outputs.spent_timestamp > $time)
        AND outputs.main_chain = true
    GROUP BY outputs.address
  """

  def updateAlphHolders(from: TimeStamp, to: TimeStamp): DBActionW[Int] =
    sqlu"""
    WITH
    inflow AS (
        SELECT outputs.address,
               COALESCE(SUM(outputs.amount), 0) AS total_received
        FROM outputs
        WHERE outputs.block_timestamp > $from
          AND outputs.block_timestamp <= $to
          AND outputs.main_chain = true
        GROUP BY outputs.address
    ),
    outflow AS (
        SELECT outputs.address,
               COALESCE(SUM(outputs.amount), 0) AS total_spent
        FROM outputs
        INNER JOIN inputs
                ON outputs.key = inputs.output_ref_key
                AND inputs.main_chain = true
        WHERE inputs.block_timestamp > $from
          AND inputs.block_timestamp <= $to
          AND outputs.main_chain = true
        GROUP BY outputs.address
    ),
    balance_diff AS (
        SELECT COALESCE(inflow.address, outflow.address) AS address,
               COALESCE(inflow.total_received, 0) AS total_received,
               COALESCE(outflow.total_spent, 0) AS total_spent
        FROM inflow
        FULL OUTER JOIN outflow ON inflow.address = outflow.address
    )
    INSERT INTO alph_holders (address, balance)
    SELECT bd.address,
           bd.total_received - bd.total_spent AS balance
    FROM balance_diff bd
    ON CONFLICT (address)
    DO UPDATE SET
        balance = alph_holders.balance + EXCLUDED.balance;
  """

  def insertInitialTokenHolders(time: TimeStamp): DBActionW[Int] =
    sqlu"""
    INSERT INTO token_holders (address, token, balance)
    SELECT token_outputs.address,
           token_outputs.token,
           COALESCE(SUM(token_outputs.amount), 0) AS total_balance
    FROM token_outputs
    WHERE
        token_outputs.block_timestamp <= $time
        AND (token_outputs.spent_finalized IS NULL OR token_outputs.spent_timestamp > $time)
        AND token_outputs.main_chain = true
    GROUP BY token_outputs.address, token_outputs.token
  """

  def updateTokenHolders(from: TimeStamp, to: TimeStamp): DBActionW[Int] =
    sqlu"""
    WITH
    inflow AS (
        SELECT token_outputs.address,
               token_outputs.token,
               COALESCE(SUM(token_outputs.amount), 0) AS total_received
        FROM token_outputs
        WHERE token_outputs.block_timestamp > $from
          AND token_outputs.block_timestamp <= $to
          AND token_outputs.main_chain = true
        GROUP BY token_outputs.address, token_outputs.token
    ),
    outflow AS (
        SELECT token_outputs.address,
               token_outputs.token,
               COALESCE(SUM(token_outputs.amount), 0) AS total_spent
        FROM token_outputs
        INNER JOIN inputs
                ON token_outputs.key = inputs.output_ref_key
                AND inputs.main_chain = true
        WHERE inputs.block_timestamp > $from
          AND inputs.block_timestamp <= $to
          AND token_outputs.main_chain = true
        GROUP BY token_outputs.address, token_outputs.token
    ),
    balance_diff AS (
        SELECT COALESCE(inflow.address, outflow.address) AS address,
               COALESCE(inflow.token, outflow.token) AS token,
               COALESCE(inflow.total_received, 0) AS total_received,
               COALESCE(outflow.total_spent, 0) AS total_spent
        FROM inflow
        FULL OUTER JOIN outflow
        ON inflow.address = outflow.address
        AND inflow.token = outflow.token
    )
    INSERT INTO token_holders (address, token, balance)
    SELECT bd.address,
           bd.token,
           bd.total_received - bd.total_spent AS balance
    FROM balance_diff bd
    ON CONFLICT (address, token)
    DO UPDATE SET
        balance = token_holders.balance + EXCLUDED.balance;
  """

}
