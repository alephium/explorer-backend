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

package org.alephium.explorer.persistence

import scala.concurrent.{ExecutionContext, Future}

import com.typesafe.scalalogging.StrictLogging
import slick.basic.DatabaseConfig
import slick.dbio.DBIOAction
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.foldFutures
import org.alephium.explorer.persistence.model.AppState.MigrationVersion
import org.alephium.explorer.persistence.queries.AppStateQueries
import org.alephium.explorer.persistence.schema.CustomGetResult._
import org.alephium.explorer.persistence.schema.CustomSetParameter._
import org.alephium.explorer.service.FinalizerService
import org.alephium.explorer.util.TimeUtil
import org.alephium.protocol.ALPH
import org.alephium.util.{Duration, TimeStamp}

@SuppressWarnings(Array("org.wartremover.warts.AnyVal"))
object Migrations extends StrictLogging {

  val latestVersion: MigrationVersion = MigrationVersion(3)

  def addInputTxHashRefColumn(): DBActionWT[Int] = {
    sqlu"""
      ALTER TABLE inputs
      ADD COLUMN IF NOT EXISTS output_ref_tx_hash bytea;
    """
  }

  //Will automatically be recreated after the migration
  def dropInputOutputRefAddressNullIndex(): DBActionWT[Int] = {
    sqlu"DROP INDEX IF EXISTS inputs_output_ref_amount_null_idx"
  }

  def updateInputTxHashRef(databaseConfig: DatabaseConfig[PostgresProfile])(
      implicit ec: ExecutionContext): Future[Unit] = {
    foldFutures(
      TimeUtil
        .buildTimestampRange(ALPH.LaunchTimestamp, TimeStamp.now(), Duration.ofDaysUnsafe(1))) {
      case (from, to) =>
        logger.info(
          s"Updating inputs tx_hash_ref: ${TimeUtil.toInstant(from)} - ${TimeUtil.toInstant(to)}")
        DBRunner.run(databaseConfig)(
          sqlu"""
      UPDATE inputs
      SET
        output_ref_tx_hash = outputs.tx_hash
      FROM outputs
      WHERE inputs.block_timestamp >= $from
      AND inputs.block_timestamp <= $to
      AND inputs.output_ref_key = outputs.key
      AND outputs.main_chain = true
      AND inputs.output_ref_tx_hash IS NULL
    """
        )
    }.map(_ => ())
  }

  def updateOutputs(finalizationTime: TimeStamp): DBActionW[Int] =
    sqlu"""
      UPDATE outputs o
      SET spent_finalized = i.tx_hash
      FROM inputs i
      WHERE i.output_ref_key = o.key
      AND o.spent_finalized IS NULL
      AND o.main_chain=true
      AND i.main_chain=true
      AND i.block_timestamp <= $finalizationTime
      """

  def updateTokenOutputs(finalizationTime: TimeStamp): DBActionW[Int] =
    sqlu"""
      UPDATE token_outputs o
      SET spent_finalized = i.tx_hash
      FROM inputs i
      WHERE i.output_ref_key = o.key
      AND o.spent_finalized IS NULL
      AND o.main_chain=true
      AND i.main_chain=true
      AND i.block_timestamp <= $finalizationTime
      """

  def updateFinalizedValue(implicit ec: ExecutionContext): DBActionWT[Unit] = {
    logger.info(s"Migrating finalized outputs and tokens")
    val finalizationTime = FinalizerService.finalizationTime
    (for {
      outs <- updateOutputs(finalizationTime)
      toks <- updateTokenOutputs(finalizationTime)
    } yield {
      logger.info(s"$outs outputs finalized")
      logger.info(s"$toks tokens finalized")
    }).transactionally
  }

  private val migration1 =
    sqlu"""
      CREATE INDEX IF NOT EXISTS txs_per_address_address_timestamp_idx
      ON transaction_per_addresses (address, block_timestamp)
    """

  private def migration2(implicit ec: ExecutionContext) =
    (for {
      _ <- addInputTxHashRefColumn()
      _ <- dropInputOutputRefAddressNullIndex()
    } yield ()).transactionally

  private def migration3(implicit ec: ExecutionContext) = updateFinalizedValue

  private def migrations(implicit ec: ExecutionContext) =
    Seq(migration1, migration2, migration3)

  def migrationsQuery(versionOpt: Option[MigrationVersion])(
      implicit ec: ExecutionContext): DBActionWT[Unit] = {
    logger.info(s"Current migration version: $versionOpt")
    versionOpt match {
      //noop
      case None | Some(MigrationVersion(latestVersion.version)) =>
        logger.info(s"No migrations needed")
        DBIOAction.successful(())
      case Some(MigrationVersion(current)) =>
        logger.info(s"Applying ${latestVersion.version - current} migrations")
        val migrationsToPerform = migrations.drop(current)
        DBIOAction
          .sequence(migrationsToPerform)
          .transactionally
          .map(_ => ())
    }
  }

  def migrate(databaseConfig: DatabaseConfig[PostgresProfile])(
      implicit ec: ExecutionContext): Future[Unit] = {
    logger.info("Migrating")
    for {
      prevVersion <- DBRunner
        .run(databaseConfig)(for {
          version <- getVersion()
          _       <- migrationsQuery(version)
        } yield version)
      _ <- if (prevVersion.map(_.version < 2).getOrElse(false)) {
        updateInputTxHashRef(databaseConfig)
      } else {
        Future.unit
      }
      _ <- DBRunner.run(databaseConfig)(updateVersion(Some(latestVersion)))
    } yield ()
  }

  def getVersion()(implicit ec: ExecutionContext): DBActionR[Option[MigrationVersion]] = {
    AppStateQueries.get(MigrationVersion)
  }

  def updateVersion(versionOpt: Option[MigrationVersion])(
      implicit ec: ExecutionContext): DBActionW[Unit] = {
    versionOpt match {
      case None => DBIOAction.successful(())
      case Some(version) =>
        AppStateQueries
          .insertOrUpdate(version)
          .map(_ => ())
    }
  }
}
