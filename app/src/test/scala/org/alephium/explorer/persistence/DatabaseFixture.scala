// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence

import scala.jdk.CollectionConverters._

import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.AlephiumFutures
import org.alephium.explorer.config._
import org.alephium.explorer.util.TestUtils._

/** Implements functions for managing test database connections.
  */
object DatabaseFixture extends AlephiumFutures {

  lazy val cleanTablesQuery = DBInitializer.allTables.map { table =>
    s"DELETE FROM ${table.baseTableRow.tableName};"
  }.mkString

  def config(dbName: String) =
    ConfigFactory
      .parseMap(
        Map(
          ("db.db.url", s"jdbc:postgresql://localhost:5432/$dbName")
        ).view.mapValues(ConfigValueFactory.fromAnyRef).toMap.asJava
      )
      .withFallback(ConfigFactory.load())

  implicit val explorerConfig: ExplorerConfig = TestExplorerConfig()

  def createDatabaseConfig(dbName: String): DatabaseConfig[PostgresProfile] =
    DatabaseConfig.forConfig[PostgresProfile]("db", config(dbName))

  def createTables()(implicit databaseConfig: DatabaseConfig[PostgresProfile]) = {
    DBInitializer.initialize().futureValue
  }

  def cleanTables()(implicit databaseConfig: DatabaseConfig[PostgresProfile]) = {
    databaseConfig.db.run(sqlu"#$cleanTablesQuery").map(_ => ()).futureValue
  }

  def createDb(dbName: String) = {
    using(DatabaseConfig.forConfig[PostgresProfile]("db", config(""))) { databaseConfig =>
      databaseConfig.db.run(sqlu"DROP DATABASE IF EXISTS #$dbName").futureValue
      databaseConfig.db.run(sqlu"CREATE DATABASE #$dbName").futureValue
    }
  }

  def dropDb(dbName: String) = {
    using(DatabaseConfig.forConfig[PostgresProfile]("db", config(""))) { databaseConfig =>
      databaseConfig.db.run(sqlu"DROP DATABASE #$dbName").futureValue
    }
  }
}
