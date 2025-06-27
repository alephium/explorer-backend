// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.benchmark.db.state

import org.openjdk.jmh.annotations.{Scope, State}

import org.alephium.explorer.benchmark.db.{DBConnectionPool, DBExecutor}
import org.alephium.explorer.benchmark.db.BenchmarkSettings._
import org.alephium.explorer.benchmark.db.table.TableVarcharSchema
import org.alephium.protocol.Hash

/** JMH state for benchmarking writes to
  * [[org.alephium.explorer.benchmark.db.table.TableVarcharSchema]].
  */
@State(Scope.Thread)
@SuppressWarnings(Array("org.wartremover.warts.Overloading"))
class VarcharWriteState(val db: DBExecutor)
    extends WriteBenchmarkState[String](db)
    with TableVarcharSchema {

  import config.profile.api._

  // Overload: default constructor required by JMH. Uses Postgres as target DB.
  def this() = {
    this(DBExecutor(dbName, dbHost, dbPort, DBConnectionPool.HikariCP))
  }

  def generateData(): String =
    Hash.generate.toHexString

  def beforeAll(): Unit =
    db.runNow(
      // action = create a fresh table
      action = tableVarcharQuery.schema.dropIfExists.andThen(tableVarcharQuery.schema.create),
      timeout = requestTimeout
    )
}
