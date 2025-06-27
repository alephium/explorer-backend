// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence

import com.typesafe.scalalogging.StrictLogging
import org.scalatest.{BeforeAndAfterAll, Suite}
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import org.alephium.explorer.AlephiumFutures
import org.alephium.explorer.config._

/** Creates and drops a new database connection for all test-cases in a test class.
  */
@SuppressWarnings(Array("org.wartremover.warts.PlatformDefault"))
trait DatabaseFixtureForAll extends BeforeAndAfterAll with AlephiumFutures with StrictLogging {
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

  override def afterAll(): Unit = {
    super.afterAll()
    databaseConfig.db.close()
    DatabaseFixture.dropDb(dbName)
    logger.debug(s"Test database $dbName dropped")
  }
}
