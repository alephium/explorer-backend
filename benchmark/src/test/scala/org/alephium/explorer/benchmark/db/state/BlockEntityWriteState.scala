// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.benchmark.db.state

import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutor}

import org.openjdk.jmh.annotations.{Scope, State}
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import org.alephium.explorer.GroupSetting
import org.alephium.explorer.benchmark.db.{DataGenerator, DBConnectionPool, DBExecutor}
import org.alephium.explorer.benchmark.db.BenchmarkSettings._
import org.alephium.explorer.cache.{BlockCache, TestBlockCache}
import org.alephium.explorer.config._
import org.alephium.explorer.persistence.DBInitializer
import org.alephium.explorer.persistence.model.BlockEntity
import org.alephium.util.Duration

/** JMH state for benchmarking block creation.
  */
@State(Scope.Thread)
@SuppressWarnings(
  Array("org.wartremover.warts.Overloading", "org.wartremover.warts.GlobalExecutionContext")
)
class BlockEntityWriteState(val db: DBExecutor) extends WriteBenchmarkState[BlockEntity](db) {

  val groupSetting: GroupSetting =
    GroupSetting(4)

  val blockCache: BlockCache =
    TestBlockCache()(
      groupSetting = groupSetting,
      ec = config.db.ioExecutionContext,
      dc = db.config
    )

  def generateData(): BlockEntity =
    DataGenerator.genBlockEntity()

  def beforeAll(): Unit = {
    implicit val ec: ExecutionContextExecutor        = ExecutionContext.global
    implicit val dc: DatabaseConfig[PostgresProfile] = config
    implicit val explorerConfig: ExplorerConfig      = TestExplorerConfig()

    Await.result(DBInitializer.dropTables(), Duration.ofSecondsUnsafe(10).asScala)
    Await.result(DBInitializer.initialize(), Duration.ofSecondsUnsafe(10).asScala)
  }
}

/** State with connection pooling disabled */
class BlockEntityWriteState_DisabledCP
    extends BlockEntityWriteState(
      DBExecutor(
        name = dbName,
        host = dbHost,
        port = dbPort,
        connectionPool = DBConnectionPool.Disabled
      )
    )

/** State with connection pooling enabled with HikariCP */
class BlockEntityWriteState_HikariCP
    extends BlockEntityWriteState(
      DBExecutor(
        name = dbName,
        host = dbHost,
        port = dbPort,
        connectionPool = DBConnectionPool.HikariCP
      )
    )
