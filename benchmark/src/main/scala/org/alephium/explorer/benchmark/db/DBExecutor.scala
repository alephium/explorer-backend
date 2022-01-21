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

package org.alephium.explorer.benchmark.db

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Random

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.StrictLogging
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import org.alephium.explorer.persistence.DBAction

object DBExecutor extends StrictLogging {

  /**
    * Builds a [[DBExecutor]] instance for Postgres.
    *
    * @param name Target database name
    * @param host Target database host
    * @param port Target database port
    */
  def forPostgres(name: String, host: String, port: Int): DBExecutor = {
    logger.info(s"Connecting to database: '$name'")
    val config =
      DatabaseConfig.forConfig[JdbcProfile](
        path = "db",
        config = ConfigFactory.parseString(
          s"""db = {
               |  profile = "slick.jdbc.PostgresProfile$$"
               |  db {
               |    connectionPool = "HikariCP"
               |    name           = $name
               |    host           = $host
               |    port           = $port
               |    user           = "postgres"
               |    url            = "jdbc:postgresql://$host:$port/$name"
               |  }
               |}
               |""".stripMargin
        )
      )

    new DBExecutor(config)
  }

  /**
    * Builds a [[DBExecutor]] instance for in-memory H2.
    */
  def forH2(): DBExecutor = {
    val config =
      DatabaseConfig.forConfig[JdbcProfile](
        path = "db",
        config = ConfigFactory.parseString(
          // format: off
          s"""db = {
               |  profile = "slick.jdbc.H2Profile$$"
               |  db {
               |    connectionPool = "HikariCP"
               |    url            = "jdbc:h2:mem:${Random.nextString(5)};DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE"
               |  }
               |}
               |""".stripMargin
          // format: on
        )
      )

    new DBExecutor(config)
  }
}

/**
  * Provides execution functions for interacting with the target ([[config]]) database.
  *
  * @param config Target database config
  *
  * @note Similar to [[org.alephium.explorer.persistence.DBRunner]] but provides blocking execution.
  *       To avoid naming conflicts it's named [[DBExecutor]] instead of `DBRunner`.
  */
class DBExecutor private (val config: DatabaseConfig[JdbcProfile]) extends StrictLogging {

  import config.profile.api._

  /**
    * Executes a database action.
    */
  def runNow[R, E <: Effect](action: DBAction[R, E], timeout: FiniteDuration): R =
    Await.result[R](config.db.run(action), timeout)

  /**
    * Closes DB connection
    */
  def close(): Unit =
    config.db.close()
}
