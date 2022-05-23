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

import scala.concurrent.{Await, ExecutionContext}
import scala.jdk.CollectionConverters._

import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import org.alephium.util.Duration

/**
  * Implements functions for managing test database connections.
  */
object DatabaseFixture {

  private val config = ConfigFactory
    .parseMap(
      Map(
        ("db.db.url", s"jdbc:postgresql://localhost:5432/postgres")
      ).view.mapValues(ConfigValueFactory.fromAnyRef).toMap.asJava)
    .withFallback(ConfigFactory.load())

  def createDatabaseConfig(): DatabaseConfig[PostgresProfile] =
    DatabaseConfig.forConfig[PostgresProfile]("db", config)

  def dropCreateTables()(implicit databaseConfig: DatabaseConfig[PostgresProfile]) = {
    implicit val ec: ExecutionContext = ExecutionContext.global

    Await.result(DBInitializer.dropTables(), Duration.ofSecondsUnsafe(10).asScala)
    Await.result(DBInitializer.initialize(), Duration.ofSecondsUnsafe(10).asScala)
  }
}
