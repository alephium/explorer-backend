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

import java.time.Instant
import java.time.temporal.ChronoUnit

import scala.concurrent.{ExecutionContext, Future}

import com.typesafe.scalalogging.StrictLogging
import slick.basic.DatabaseConfig
import slick.dbio.DBIOAction
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.persistence.model.AppState.{LastFinalizedInputTime, MigrationVersion}
import org.alephium.explorer.persistence.queries.{AppStateQueries, ContractQueries}
import org.alephium.explorer.persistence.schema._
import org.alephium.explorer.persistence.schema.CustomGetResult._
import org.alephium.util.TimeStamp

@SuppressWarnings(Array("org.wartremover.warts.AnyVal"))
object Migrations extends StrictLogging {

  val latestVersion: MigrationVersion = MigrationVersion(6)

  def migration1(implicit ec: ExecutionContext): DBActionAll[Unit] =
    EventSchema.table.result.flatMap { events =>
      for {
        _ <- ContractQueries.insertContractCreation(events)
        _ <- ContractQueries.updateContractDestruction(events)
      } yield ()
    }

  def migration2(implicit ec: ExecutionContext): DBActionAll[Unit] =
    for {
      _ <- sqlu"""ALTER TABLE contracts ADD COLUMN IF NOT EXISTS std_interface_id_guessed bytea"""
      _ <-
        sqlu"""CREATE INDEX IF NOT EXISTS contracts_std_interface_id_guessed_idx ON contracts (std_interface_id_guessed)"""
    } yield ()

  def migration3(implicit ec: ExecutionContext): DBActionAll[Unit] =
    for {
      _ <- sqlu"""ALTER TABLE outputs ADD COLUMN IF NOT EXISTS spent_timestamp bigint"""
      _ <- sqlu"""ALTER TABLE token_outputs ADD COLUMN IF NOT EXISTS spent_timestamp bigint"""
      _ <-
        sqlu"""CREATE INDEX IF NOT EXISTS outputs_spent_timestamp_idx ON outputs(spent_timestamp)"""
      _ <-
        sqlu"""CREATE INDEX IF NOT EXISTS token_outputs_spent_timestamp_idx ON token_outputs(spent_timestamp)"""
      // Reset `last_finalized_input_time` and let `FinalizerService` update all `spent_timestamp`
      _ <- sqlu"""DELETE FROM app_state WHERE key = 'last_finalized_input_time'"""
    } yield ()

  def migration4(implicit ec: ExecutionContext): DBActionAll[Unit] = {
    val currentDay =
      Instant.ofEpochMilli(TimeStamp.now().millis).truncatedTo(ChronoUnit.DAYS).toEpochMilli
    for {
      lastFinalizedInputTimeOpt <- AppStateQueries.get(LastFinalizedInputTime)
      _ <-
        if (lastFinalizedInputTimeOpt.map(_.time.millis < currentDay).getOrElse(true)) {
          DBIOAction.failed(
            new Throwable(
              "`spent_timestamp` update isn't finish, please continue to run version `v1.14.1`"
            )
          )
        } else {
          DBIOAction.successful(())
        }
    } yield ()
  }

  def migration5(implicit ec: ExecutionContext): DBActionAll[Unit] = {
    for {
      _ <- sqlu"""ALTER TABLE token_info ADD COLUMN IF NOT EXISTS interface_id character varying"""
      _ <- sqlu"""ALTER TABLE token_info ADD COLUMN IF NOT EXISTS category character varying"""
      _ <- sqlu"""CREATE INDEX token_info_interface_id_idx ON token_info(interface_id)"""
      _ <- sqlu"""CREATE INDEX token_info_category_idx ON token_info(category)"""
      _ <- sqlu"""ALTER TABLE contracts ADD COLUMN IF NOT EXISTS interface_id character varying"""
      _ <- sqlu"""ALTER TABLE contracts ADD COLUMN IF NOT EXISTS category character varying"""
      _ <- sqlu"""CREATE INDEX contracts_interface_id_idx ON contracts(interface_id)"""
      _ <- sqlu"""CREATE INDEX contracts_category_idx ON contracts(category)"""
    } yield ()
  }

  def migration6(implicit ec: ExecutionContext): DBActionAll[Unit] = {
    for {
      _ <- sqlu"""ALTER TABLE latest_blocks ADD COLUMN IF NOT EXISTS deps bytea"""
    } yield ()
  }

  private def migrations(implicit ec: ExecutionContext): Seq[DBActionAll[Unit]] = Seq(
    migration1,
    migration2,
    migration3,
    migration4,
    migration5,
    migration6
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
