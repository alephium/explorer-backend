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

package org.alephium.explorer.benchmark.postgres

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.StrictLogging
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.persistence.DBRunner

object PostgresConnection extends StrictLogging {

  /**
    * Establishes a connection with Postgres for a given table.
    *
    * If the table already exists, it drops and recreates it.
    *
    * Prerequisite: The database should already exist.
    *
    * @return An instance of [[PostgresConnection]]
    */
  @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
  def apply[T <: Table[_]](
      dbName: String,
      table: TableQuery[T],
      host: String = "localhost",
      port: Int    = 5432)(implicit ec: ExecutionContext): PostgresConnection[T] = {
    logger.info(s"Connecting to database: '$dbName' for Table: '${table.baseTableRow.tableName}'")
    new PostgresConnection(
      dbName = dbName,
      table  = table,
      config = createConfig(
        dbName = dbName,
        host   = host,
        port   = port
      )
    ).runDropAndCreateTableBlocking()
  }

  //builds DatabaseConfig
  def createConfig(dbName: String, host: String, port: Int): DatabaseConfig[JdbcProfile] =
    DatabaseConfig.forConfig[JdbcProfile](
      path = "db",
      config = parseConfig(
        dbName = dbName,
        host   = host,
        port   = port
      )
    )

  /**
    * Builds the [[Config]] configuring a postgres instance
    * with metrics disabled.
    */
  def parseConfig(dbName: String, host: String, port: Int): Config =
    ConfigFactory.parseString(
      s"""db = {
         |  profile = "slick.jdbc.PostgresProfile$$"
         |  db {
         |    connectionPool         = "org.alephium.explorer.InstrumentedHikariCP$$"
         |    enableDatabaseMetrics  = false
         |    name                   = $dbName
         |    host                   = $host
         |    port                   = $port
         |    url                    = "jdbc:postgresql://$host:$port/$dbName"
         |  }
         |}
         |""".stripMargin
    )
}

/**
  * Establishes a database connection and provides functions to manage the lifecycle
  * of the [[table]] being benchmarked.
  *
  * @param dbName Database name
  * @param table  The table being benchmarked
  * @param config Database configurations
  * @tparam T Table type
  */
class PostgresConnection[T <: Table[_]] private (val dbName: String,
                                                 val table: TableQuery[T],
                                                 val config: DatabaseConfig[JdbcProfile])
    extends DBRunner
    with StrictLogging {

  /**
    * Builds a query to drop and create the [[table]] clearing exiting data.
    */
  def dropCreateTableQuery()(implicit ec: ExecutionContext): DBIO[PostgresConnection[T]] =
    table.schema.dropIfExists
      .andThen(table.schema.create)
      .map(_ => this)

  /**
    * Drops and creates the [[table]] blocking on current thread.
    */
  def runDropAndCreateTableBlocking()(implicit ec: ExecutionContext): PostgresConnection[T] = {
    logger.info(
      s"Dropping and creating table '${table.baseTableRow.tableName}' in Database: '$dbName'")
    Await.result(run(dropCreateTableQuery()), 10.seconds)
  }

  /**
    * Drops the [[table]] blocking on current thread.
    */
  def runDropTableBlocking()(implicit ec: ExecutionContext): Unit = {
    logger.info(s"Dropping table '${table.baseTableRow.tableName}' in Database: '$dbName'")
    Await.result(run(table.schema.dropIfExists), 10.seconds)
  }

  /**
    * Drops the tables and closes the DB connection.
    */
  def terminateBlocking()(implicit ec: ExecutionContext): Unit = {
    logger.info(s"Terminating '${table.baseTableRow.tableName}' in Database: '$dbName'")
    runDropTableBlocking()
    close()
  }

  /**
    * Closes DB connection
    */
  def close(): Unit =
    config.db.close()
}
