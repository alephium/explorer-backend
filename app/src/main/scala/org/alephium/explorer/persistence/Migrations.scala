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

import org.alephium.explorer.persistence.model.AppState.MigrationVersion
import org.alephium.explorer.persistence.queries.AppStateQueries

object Migrations extends StrictLogging {

  def migrations(versionOpt: Option[MigrationVersion]): DBActionWT[Option[MigrationVersion]] = {
    logger.info(s"Current migration version: $versionOpt")
    versionOpt match {
      case Some(MigrationVersion(0)) => DBIOAction.successful(None)
      case _                         => DBIOAction.successful(Some(MigrationVersion(0)))
    }
  }

  def migrate(databaseConfig: DatabaseConfig[PostgresProfile])(
      implicit ec: ExecutionContext): Future[Unit] = {
    logger.info("Migrating")
    DBRunner
      .run(databaseConfig)(for {
        version       <- getVersion()
        newVersionOpt <- migrations(version)
        _             <- updateVersion(newVersionOpt)
      } yield ())
  }

  def getVersion()(implicit ec: ExecutionContext): DBActionR[Option[MigrationVersion]] = {
    AppStateQueries.get(MigrationVersion).map {
      case None                            => None
      case Some(MigrationVersion(version)) => Some(MigrationVersion(version))
      case _ =>
        logger.error(s"Invalid migration version, closing app.")
        sys.exit(1)
    }
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
