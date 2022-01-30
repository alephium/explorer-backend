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

import org.alephium.explorer.Hash
import org.alephium.explorer.benchmark.db.{DBConnectionPool, DBExecutor}
import org.alephium.explorer.benchmark.db.BenchmarkSettings._
import org.alephium.explorer.benchmark.db.table.TableVarcharSchema

/**
  * JMH state for benchmarking reads to [[TableByteSchema]].
  *
  * @param testDataCount total number of rows to generate in [[TableByteSchema]]
  */
@State(Scope.Thread)
@SuppressWarnings(Array("org.wartremover.warts.Overloading"))
class VarcharReadState(testDataCount: Int, val db: DBExecutor)
    extends ReadBenchmarkState[String](testDataCount = testDataCount, db = db)
    with TableVarcharSchema {

  import config.profile.api._

  //Overload: default constructor required by JMH. Uses Postgres as target DB.
  def this() = {
    this(readDataCount, DBExecutor(dbName, dbHost, dbPort, DBConnectionPool.HikariCP))
  }

  def generateData(currentCacheSize: Int): String =
    Hash.generate.toHexString

  def persist(data: Array[String]): Unit = {
    //create a fresh table and insert the data
    val query =
      tableVarcharQuery.schema.dropIfExists
        .andThen(tableVarcharQuery.schema.create)
        .andThen(tableVarcharQuery ++= data)

    val _ = db.runNow(
      action  = query,
      timeout = batchWriteTimeout
    )
  }
}
