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

  def start(interval: FiniteDuration)(implicit
      executionContext: ExecutionContext,
      databaseConfig: DatabaseConfig[PostgresProfile],
      scheduler: Scheduler
  ): Future[Unit] = {
    scheduler.scheduleLoop(
      taskId = FinalizerService.productPrefix,
      firstInterval = ScalaDuration.Zero,
      loopInterval = interval
    )(syncOnce())
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
  ): Future[Unit] = run(for {
    _ <- insertInitialAlphHolders(lastFinalizedInputTime)
    _ <- insertInitialTokenHolders(lastFinalizedInputTime)
    _ <- AppStateQueries.insertOrUpdate(LastHoldersUpdate(lastFinalizedInputTime))
  } yield ())

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
    INSERT INTO holders (address, balance)
    SELECT outputs.address,
           COALESCE(SUM(outputs.amount), 0) AS total_balance
    FROM outputs
    WHERE
        outputs.block_timestamp <= $time
        AND (outputs.spent_finalized IS NULL OR outputs.spent_timestamp > $time)
        AND outputs.main_chain = true
    GROUP BY outputs.address
    ORDER BY total_balance DESC;
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
    INSERT INTO holders (address, balance)
    SELECT bd.address,
           bd.total_received - bd.total_spent AS balance
    FROM balance_diff bd
    ON CONFLICT (address)
    DO UPDATE SET
        balance = holders.balance + EXCLUDED.balance;
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
    ORDER BY total_balance DESC;
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
