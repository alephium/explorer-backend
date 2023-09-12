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

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.{Duration => ScalaDuration, FiniteDuration}

import com.typesafe.scalalogging.StrictLogging
import slick.basic.DatabaseConfig
import slick.dbio.DBIOAction
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.foldFutures
import org.alephium.explorer.persistence._
import org.alephium.explorer.persistence.DBRunner._
import org.alephium.explorer.persistence.model.AppState.BackgroundMigrationVersion
import org.alephium.explorer.persistence.queries.AppStateQueries
import org.alephium.explorer.persistence.schema.CustomGetResult._
import org.alephium.explorer.persistence.schema.CustomSetParameter._
import org.alephium.explorer.util.{Scheduler, TimeUtil}
import org.alephium.explorer.util.SlickUtil._
import org.alephium.util.{Duration, TimeStamp}

/*
 * Syncing mempool
 */

case object BackgroundMigrations extends StrictLogging {

  // scalastyle:off magic.number
  val finalizationDuration: Duration = Duration.ofSecondsUnsafe(6500)
  def finalizationTime: TimeStamp    = TimeStamp.now().minusUnsafe(finalizationDuration)
  def rangeStep: Duration            = Duration.ofHoursUnsafe(24)
  // scalastyle:on magic.number

  def start(interval: FiniteDuration)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      blockFlowClient: BlockFlowClient,
  ): Future[Unit] ={
    Future.unit
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

  def getVersion()(implicit ec: ExecutionContext): DBActionAll[Option[BackgroundMigrationVersion]] = {
    AppStateQueries.get(BackgroundMigrationVersion)
  }

  def updateVersion(
      versionOpt: Option[BackgroundMigrationVersion]
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
