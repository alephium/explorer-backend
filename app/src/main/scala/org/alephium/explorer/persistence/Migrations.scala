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

import akka.util.ByteString
import com.typesafe.scalalogging.StrictLogging
import slick.basic.DatabaseConfig
import slick.dbio.DBIOAction
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.persistence.model.AppState.MigrationVersion
import org.alephium.explorer.persistence.queries.AppStateQueries
import org.alephium.explorer.persistence.schema.CustomGetResult._
import org.alephium.serde._

object Migrations extends StrictLogging {

  private val addUOutputOrderColumn: DBActionW[Int] = sqlu"""
    ALTER TABLE uoutputs
    ADD COLUMN IF NOT EXISTS "uoutput_order" INTEGER DEFAULT 0;
  """

  private val updateUOutputPK =
    sqlu"""
      ALTER TABLE uoutputs
      DROP CONSTRAINT IF EXISTS uoutputs_pk CASCADE,
      ADD CONSTRAINT uoutputs_pk PRIMARY KEY(tx_hash, address, uoutput_order)
    """

  private val addUTransactionLastSeenColumn: DBActionW[Int] = sqlu"""
    ALTER TABLE utransactions
    ADD COLUMN IF NOT EXISTS "last_seen" BIGINT DEFAULT 0;
  """

  def migrations(version: Int)(implicit ec: ExecutionContext): DBActionWT[Option[Int]] = {
    logger.debug(s"Current migration version: $version")
    if (version == 0) {
      for {
        _ <- addUOutputOrderColumn
        _ <- updateUOutputPK
        _ <- addUTransactionLastSeenColumn
      } yield Some(1)
    } else {
      DBIOAction.successful(None)
    }
  }

  def migrate(databaseConfig: DatabaseConfig[PostgresProfile])(
      implicit ec: ExecutionContext): Future[Option[Int]] = {
    logger.info("Migrating")
    DBRunner
      .run(databaseConfig)(for {
        version       <- getVersion()
        newVersionOpt <- migrations(version)
        _             <- updateVersion(newVersionOpt)
      } yield newVersionOpt)
  }

  def getVersion()(implicit ec: ExecutionContext): DBActionR[Int] = {
    sql"""
      SELECT value FROM app_state where key = 'migrations_version'
    """.as[ByteString].headOption.map {
      case None => 0
      case Some(bytes) =>
        deserialize[Int](bytes) match {
          case Left(error) =>
            logger.error(s"Invalid migration version, closing app. $error")
            sys.exit(1)
          case Right(version) => version
        }
    }
  }

  def updateVersion(versionOpt: Option[Int])(implicit ec: ExecutionContext): DBActionW[Unit] = {
    versionOpt match {
      case None => DBIOAction.successful(())
      case Some(version) =>
        AppStateQueries
          .insertOrUpdate(MigrationVersion(version))
          .map(_ => ())
    }
  }
}
