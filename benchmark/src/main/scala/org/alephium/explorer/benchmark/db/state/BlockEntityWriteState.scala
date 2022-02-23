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

import scala.concurrent.{Await, ExecutionContext}

import org.openjdk.jmh.annotations.{Scope, State}

import org.alephium.explorer.benchmark.db.{DataGenerator, DBConnectionPool, DBExecutor}
import org.alephium.explorer.benchmark.db.BenchmarkSettings._
import org.alephium.explorer.persistence.DBInitializer
import org.alephium.explorer.persistence.dao.BlockDao
import org.alephium.explorer.persistence.model.BlockEntity
import org.alephium.explorer.persistence.schema._
import org.alephium.util.Duration

/**
  * JMH state for benchmarking block creation.
  */
@State(Scope.Thread)
@SuppressWarnings(Array("org.wartremover.warts.Overloading"))
class BlockEntityWriteState(val db: DBExecutor)
    extends WriteBenchmarkState[BlockEntity](db)
    with BlockHeaderSchema
    with TransactionSchema
    with InputSchema
    with OutputSchema
    with BlockDepsSchema {

  val dao: BlockDao =
    BlockDao(4, config)(db.config.db.ioExecutionContext)

  def generateData(): BlockEntity =
    DataGenerator.genBlockEntity()

  def beforeAll(): Unit = {
    val dbInitializer: DBInitializer = new DBInitializer(config)(ExecutionContext.global)
    Await.result(dbInitializer.dropTables(), Duration.ofSecondsUnsafe(10).asScala)
    Await.result(dbInitializer.createTables(), Duration.ofSecondsUnsafe(10).asScala)
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
