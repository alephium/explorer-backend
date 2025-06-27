// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence

import com.typesafe.scalalogging.StrictLogging
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Suite}
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import org.alephium.explorer.AlephiumFutures
import org.alephium.explorer.config._

/** Creates and drops a new database connection for each test-case.
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

  implicit val explorerConfig: ExplorerConfig = TestExplorerConfig()

  override def beforeAll(): Unit = {
    super.beforeAll()
    DatabaseFixture.createDb(dbName)
    DatabaseFixture.createTables()
    logger.debug(s"Test database $dbName created")
  }
  override def beforeEach(): Unit = {
    super.beforeEach()
    DatabaseFixture.cleanTables()
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
