// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.benchmark.db.state

import org.openjdk.jmh.annotations.{Scope, State}

import org.alephium.explorer.benchmark.db.{DBConnectionPool, DBExecutor}
import org.alephium.explorer.benchmark.db.BenchmarkSettings._
import org.alephium.explorer.benchmark.db.table.TableVarcharSchema
import org.alephium.protocol.Hash

/** JMH state for benchmarking reads to
  * [[org.alephium.explorer.benchmark.db.table.TableByteSchema]].
  *
  * @param testDataCount
  *   total number of rows to generate in
  *   [[org.alephium.explorer.benchmark.db.table.TableByteSchema]]
  */
@State(Scope.Thread)
@SuppressWarnings(Array("org.wartremover.warts.Overloading"))
class VarcharReadState(testDataCount: Int, val db: DBExecutor)
    extends ReadBenchmarkState[String](testDataCount = testDataCount, db = db)
    with TableVarcharSchema {

  import config.profile.api._

  // Overload: default constructor required by JMH. Uses Postgres as target DB.
  def this() = {
    this(readDataCount, DBExecutor(dbName, dbHost, dbPort, DBConnectionPool.HikariCP))
  }

  def generateData(currentCacheSize: Int): String =
    Hash.generate.toHexString

  def persist(data: Array[String]): Unit = {
    // create a fresh table and insert the data
    val query =
      tableVarcharQuery.schema.dropIfExists
        .andThen(tableVarcharQuery.schema.create)
        .andThen(tableVarcharQuery ++= data)

    val _ = db.runNow(
      action = query,
      timeout = batchWriteTimeout
    )
  }
}
