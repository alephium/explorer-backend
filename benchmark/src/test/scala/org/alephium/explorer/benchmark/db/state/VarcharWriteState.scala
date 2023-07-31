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
