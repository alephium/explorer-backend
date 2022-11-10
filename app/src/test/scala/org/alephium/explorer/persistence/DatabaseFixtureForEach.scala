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

import com.typesafe.scalalogging.StrictLogging
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Suite}
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import org.alephium.explorer.AlephiumFutures

/**
  * Creates and drops a new database connection for each test-case.
  */
@SuppressWarnings(Array("org.wartremover.warts.PlatformDefault"))
trait DatabaseFixtureForEach
    extends BeforeAndAfterEach
    with BeforeAndAfterAll
    with AlephiumFutures
    with StrictLogging {
  this: Suite =>

  val dbName: String = getClass.getSimpleName.toLowerCase
  implicit val databaseConfig: DatabaseConfig[PostgresProfile] =
    DatabaseFixture.createDatabaseConfig(dbName)

  override def beforeAll(): Unit = {
    super.beforeAll()
    DatabaseFixture.createDb(dbName)
    logger.debug(s"Test database $dbName created")
  }
  override def beforeEach(): Unit = {
    super.beforeEach()
    DatabaseFixture.dropCreateTables()
  }

  override def afterEach(): Unit = {
    super.afterEach()
  }

  override def afterAll(): Unit = {
    super.afterAll()
    DatabaseFixture.dropDb(dbName)
    logger.debug(s"Test database $dbName dropped")
    databaseConfig.db.close()
  }
}
