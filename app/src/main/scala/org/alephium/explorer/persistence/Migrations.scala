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

import org.alephium.explorer.persistence._
import org.alephium.explorer.persistence.model.AppState
import org.alephium.explorer.persistence.schema.AppStateSchema
import org.alephium.explorer.persistence.schema.CustomGetResult._
import org.alephium.serde._

object Migrations extends StrictLogging {
  val addOutputStatusColumn: DBActionW[Int] = sqlu"""
    ALTER TABLE outputs
    ADD COLUMN IF NOT EXISTS "spent_finalized" BYTEA DEFAULT NULL;
  """

  val resetTokenSupply: DBActionW[Int] = sqlu"""
    DELETE FROM token_supply
  """

  def migrations(version: Int)(implicit ec: ExecutionContext): DBActionW[Option[Int]] = {
    if (version == 0) {
      for {
        _ <- addOutputStatusColumn
        _ <- resetTokenSupply
      } yield (Some(1))
    } else {
      DBIOAction.successful(None)
    }
  }

  def migrate(databaseConfig: DatabaseConfig[PostgresProfile])(
      implicit ec: ExecutionContext): Future[Unit] = {
    logger.info("Migrating")
    DBRunner.run(databaseConfig)(for {
      version       <- getVersion()
      newVersionOpt <- migrations(version)
      _             <- updateVersion(newVersionOpt)
    } yield ())
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
            System.exit(1)
            0
          case Right(version) => version
        }
    }
  }

  def updateVersion(versionOpt: Option[Int])(implicit ec: ExecutionContext): DBActionW[Unit] = {
    versionOpt match {
      case None => DBIOAction.successful(())
      case Some(version) =>
        AppStateSchema.table
          .insertOrUpdate(AppState("migrations_version", serialize(version)))
          .map(_ => ())
    }
  }
}
