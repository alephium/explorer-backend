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
import org.alephium.explorer.util.TimeUtil
import org.alephium.protocol.ALPH
import org.alephium.util.{Duration, TimeStamp}

object Migrations extends StrictLogging {

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
      AND inputs.block_timestamp < $to
      AND inputs.output_ref_key = outputs.key
      AND outputs.main_chain = true
      AND inputs.output_ref_tx_hash IS NULL
    """
        )
    }.map(_ => ())
  }

  def migrations(versionOpt: Option[MigrationVersion])(
      implicit ec: ExecutionContext): DBActionWT[Option[MigrationVersion]] = {
    logger.info(s"Current migration version: $versionOpt")
    versionOpt match {
      case Some(MigrationVersion(0)) =>
        for {
          _ <- sqlu"""CREATE INDEX IF NOT EXISTS txs_per_address_address_timestamp_idx
                  ON transaction_per_addresses (address, block_timestamp)"""
        } yield Some(MigrationVersion(1))
      case Some(MigrationVersion(1)) =>
        (for {
          _ <- addInputTxHashRefColumn()
          _ <- dropInputOutputRefAddressNullIndex()
        } yield Some(MigrationVersion(2))).transactionally
      case _ => DBIOAction.successful(Some(MigrationVersion(2)))
    }
  }

  def migrate(databaseConfig: DatabaseConfig[PostgresProfile])(
      implicit ec: ExecutionContext): Future[Unit] = {
    logger.info("Migrating")
    for {
      (prevVersion, newVersion) <- DBRunner
        .run(databaseConfig)(for {
          version       <- getVersion()
          newVersionOpt <- migrations(version)
        } yield (version, newVersionOpt))
      _ <- if (prevVersion == Some(MigrationVersion(1))) {
        updateInputTxHashRef(databaseConfig)
      } else {
        Future.unit
      }
      _ <- DBRunner.run(databaseConfig)(updateVersion(newVersion))
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
