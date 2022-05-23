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

package org.alephium.explorer.benchmark.db.state

import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutor}

import org.openjdk.jmh.annotations.{Scope, State}
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import org.alephium.explorer.GroupSetting
import org.alephium.explorer.benchmark.db.{DataGenerator, DBConnectionPool, DBExecutor}
import org.alephium.explorer.benchmark.db.BenchmarkSettings._
import org.alephium.explorer.cache.BlockCache
import org.alephium.explorer.persistence.DBInitializer
import org.alephium.explorer.persistence.model.BlockEntity
import org.alephium.util.Duration

/**
  * JMH state for benchmarking block creation.
  */
@State(Scope.Thread)
@SuppressWarnings(Array("org.wartremover.warts.Overloading"))
class BlockEntityWriteState(val db: DBExecutor) extends WriteBenchmarkState[BlockEntity](db) {

  val groupSetting: GroupSetting =
    GroupSetting(4)

  val blockCache: BlockCache =
    BlockCache()(
      groupSetting = groupSetting,
      ec           = config.db.ioExecutionContext,
      dc           = db.config
    )

  def generateData(): BlockEntity =
    DataGenerator.genBlockEntity()

  def beforeAll(): Unit = {
    implicit val ec: ExecutionContextExecutor        = ExecutionContext.global
    implicit val dc: DatabaseConfig[PostgresProfile] = config

    Await.result(DBInitializer.dropTables(), Duration.ofSecondsUnsafe(10).asScala)
    Await.result(DBInitializer.initialize(), Duration.ofSecondsUnsafe(10).asScala)
  }
}

/** State with connection pooling disabled */
class BlockEntityWriteState_DisabledCP
    extends BlockEntityWriteState(
      DBExecutor(name           = dbName,
                 host           = dbHost,
                 port           = dbPort,
                 connectionPool = DBConnectionPool.Disabled)
    )

/** State with connection pooling enabled with HikariCP */
class BlockEntityWriteState_HikariCP
    extends BlockEntityWriteState(
      DBExecutor(name           = dbName,
                 host           = dbHost,
                 port           = dbPort,
                 connectionPool = DBConnectionPool.HikariCP)
    )
