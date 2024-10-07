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

import org.alephium.explorer.persistence.model.AppState.MigrationVersion
import org.alephium.explorer.persistence.queries.AppStateQueries
import org.alephium.explorer.persistence.schema.CustomGetResult._

@SuppressWarnings(Array("org.wartremover.warts.AnyVal"))
object Migrations extends StrictLogging {

  val latestVersion: MigrationVersion = MigrationVersion(3)

  def migration1(implicit ec: ExecutionContext): DBActionAll[Unit] = {
    // We retrigger the download of fungible and non-fungible tokens' metadata that have sub-category
    for {
      _ <-
        sqlu"""UPDATE token_info SET interface_id = NULL WHERE category = '0001' AND interface_id != '0001'"""
      _ <-
        sqlu"""UPDATE token_info SET interface_id = NULL WHERE category = '0003' AND interface_id != '0003'"""
    } yield ()
  }

  def migration2(implicit ec: ExecutionContext): DBActionAll[Unit] = {
    // Finalize missing outputs and token_outputs
    // Due to finalization concurrency issue.
    for {
      _ <-
        sqlu"""
          UPDATE outputs o
          SET spent_finalized = i.tx_hash, spent_timestamp = i.block_timestamp
          FROM inputs i
          WHERE i.output_ref_key = o.key
          AND o.main_chain=true
          AND i.main_chain=true
          AND o.spent_finalized IS NULL
        """
      _ <-
        sqlu"""
          UPDATE token_outputs o
          SET spent_finalized = i.tx_hash, spent_timestamp = i.block_timestamp
          FROM inputs i
          WHERE i.output_ref_key = o.key
          AND o.main_chain=true
          AND i.main_chain=true
          AND o.spent_finalized IS NULL
        """
    } yield ()
  }

  def migration3(implicit ec: ExecutionContext): DBActionAll[Unit] = {
    // Reset token_supply table as some supply was wrong due to the finalization concurrency issue
    // Token supply will be re-computed from scratch, this will take some time until the newest
    // latest supply is computed.
    for {
      _ <-
        sqlu"""
          TRUNCATE token_supply
        """
    } yield ()
  }

  private def migrations(implicit ec: ExecutionContext): Seq[DBActionAll[Unit]] = Seq(
    migration1,
    migration2,
    migration3
  )

  def migrationsQuery(
      versionOpt: Option[MigrationVersion]
  )(implicit ec: ExecutionContext): DBActionAll[Unit] = {
    logger.info(s"Current migration version: $versionOpt")
    versionOpt match {
      // noop
      case None | Some(MigrationVersion(latestVersion.version)) =>
        logger.info(s"No migrations needed")
        DBIOAction.successful(())
      case Some(MigrationVersion(current)) if current > latestVersion.version =>
        throw new Exception("Incompatible migration versions, please reset your database")
      case Some(MigrationVersion(current)) =>
        logger.info(s"Applying ${latestVersion.version - current} migrations")
        val migrationsToPerform = migrations.drop(current)
        DBIOAction
          .sequence(migrationsToPerform)
          .transactionally
          .map(_ => ())
    }
  }

  def migrate(
      databaseConfig: DatabaseConfig[PostgresProfile]
  )(implicit ec: ExecutionContext): Future[Unit] = {
    logger.info("Migrating")
    for {
      _ <- DBRunner
        .run(databaseConfig)(for {
          version <- getVersion()
          _       <- migrationsQuery(version)
        } yield version)
      _ <- DBRunner.run(databaseConfig)(updateVersion(Some(latestVersion)))
    } yield ()
  }

  def getVersion()(implicit ec: ExecutionContext): DBActionAll[Option[MigrationVersion]] = {
    AppStateQueries.get(MigrationVersion)
  }

  def updateVersion(
      versionOpt: Option[MigrationVersion]
  )(implicit ec: ExecutionContext): DBActionAll[Unit] = {
    versionOpt match {
      case None => DBIOAction.successful(())
      case Some(version) =>
        AppStateQueries
          .insertOrUpdate(version)
          .map(_ => ())
    }
  }
}
