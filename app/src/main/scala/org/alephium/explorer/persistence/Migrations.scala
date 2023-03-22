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
import org.alephium.explorer.persistence.queries.{AppStateQueries, ContractQueries}
import org.alephium.explorer.persistence.schema.CustomGetResult._
import org.alephium.explorer.persistence.schema.EventSchema

@SuppressWarnings(Array("org.wartremover.warts.AnyVal"))
object Migrations extends StrictLogging {

  val latestVersion: MigrationVersion = MigrationVersion(1)

  def migration1(implicit ec: ExecutionContext): DBActionRW[Unit] =
    EventSchema.table.result.flatMap { events =>
      for {
        _ <- ContractQueries.insertContractCreation(events)
        _ <- ContractQueries.updateContractDestruction(events)
      } yield ()
    }

  private def migrations(implicit ec: ExecutionContext): Seq[DBActionRW[Unit]] = Seq(
    migration1
  )

  def migrationsQuery(versionOpt: Option[MigrationVersion])(
      implicit ec: ExecutionContext): DBActionRWT[Unit] = {
    logger.info(s"Current migration version: $versionOpt")
    versionOpt match {
      //noop
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

  def migrate(databaseConfig: DatabaseConfig[PostgresProfile])(
      implicit ec: ExecutionContext): Future[Unit] = {
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
