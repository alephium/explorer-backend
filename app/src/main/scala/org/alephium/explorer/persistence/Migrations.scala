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
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.persistence._

object Migrations extends StrictLogging {
  val addOutputStatusColumn: DBActionW[Int] = sqlu"""
    ALTER TABLE outputs
    ADD COLUMN IF NOT EXISTS "spent_finalized" BYTEA DEFAULT NULL;
  """

  def migrate(databaseConfig: DatabaseConfig[PostgresProfile])(
      implicit ec: ExecutionContext): Future[Unit] = {
    logger.info("Migrating")
    DBRunner.run(databaseConfig)(for {
      _ <- Migrations.addOutputStatusColumn
    } yield ())
  }
}
