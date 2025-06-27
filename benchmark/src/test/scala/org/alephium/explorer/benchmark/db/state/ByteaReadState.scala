// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.benchmark.db.state

import org.openjdk.jmh.annotations.{Scope, State}

import org.alephium.explorer.benchmark.db.{DBConnectionPool, DBExecutor}
import org.alephium.explorer.benchmark.db.BenchmarkSettings._
import org.alephium.explorer.benchmark.db.table.TableByteSchema
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
class ByteaReadState(testDataCount: Int, val db: DBExecutor)
    extends ReadBenchmarkState[Array[Byte]](testDataCount = testDataCount, db = db)
    with TableByteSchema {

  import config.profile.api._

  // Overload: default constructor required by JMH. Uses Postgres as target DB.
  def this() = {
    this(readDataCount, DBExecutor(dbName, dbHost, dbPort, DBConnectionPool.HikariCP))
  }

  def generateData(currentCacheSize: Int): Array[Byte] =
    Hash.generate.bytes.toArray

  def persist(data: Array[Array[Byte]]): Unit = {
    // create a fresh table and insert the data
    val query =
      tableByteaQuery.schema.dropIfExists
        .andThen(tableByteaQuery.schema.create)
        .andThen(tableByteaQuery ++= data)

    val _ = db.runNow(
      action = query,
      timeout = batchWriteTimeout // set this according to the value of testDataCount
    )
  }
}
