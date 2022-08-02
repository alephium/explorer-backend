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

import scala.concurrent.ExecutionContext
import scala.util._

import org.scalatest.concurrent.ScalaFutures
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import org.alephium.explorer.AlephiumSpec

/** Temporary placeholder. These tests should be merged into ApplicationSpec  */
class DatabaseSpec extends AlephiumSpec with ScalaFutures {

  implicit val executionContext: ExecutionContext =
    ExecutionContext.global

  "initialiseDatabase" should {
    "successfully connect" when {
      "readOnly mode" in {
        val databaseConfig = DatabaseConfig.forConfig[PostgresProfile]("db", DatabaseFixture.config)
        val database: Database =
          new Database(readOnly = true)(executionContext, databaseConfig)

        Try(database.startSelfOnce().futureValue) is Success(())
      }

      "readWrite mode" in {
        val databaseConfig = DatabaseConfig.forConfig[PostgresProfile]("db", DatabaseFixture.config)
        val database: Database =
          new Database(readOnly = false)(executionContext, databaseConfig)

        Try(database.startSelfOnce().futureValue) is Success(())
      }
    }
  }
}
