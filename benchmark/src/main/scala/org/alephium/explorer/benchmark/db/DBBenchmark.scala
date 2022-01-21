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

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._

import org.alephium.explorer.Hash
import org.alephium.explorer.benchmark.db.BenchmarkSettings._
import org.alephium.explorer.benchmark.db.table.{TableByteSchema, TableVarcharSchema}

/**
  * JMH state for benchmarking writes to [[TableByteSchema]].
  */
@State(Scope.Thread)
@SuppressWarnings(Array("org.wartremover.warts.Overloading"))
class ByteaWriteState(val db: DBExecutor)
    extends WriteBenchmarkState[Array[Byte]](db)
    with TableByteSchema {

  import config.profile.api._

  //Overload: default constructor required by JMH. Uses Postgres as target DB.
  def this() = {
    this(DBExecutor.forPostgres(dbName, dbHost, dbPort))
  }

  def generateData(): Array[Byte] =
    Hash.generate.bytes.toArray

  def beforeAll(): Unit =
    db.runNow(
      //action = create a fresh table
      action  = tableByteaQuery.schema.dropIfExists.andThen(tableByteaQuery.schema.create),
      timeout = requestTimeout
    )
}

/**
  * JMH state for benchmarking writes to [[TableVarcharSchema]].
  */
@State(Scope.Thread)
@SuppressWarnings(Array("org.wartremover.warts.Overloading"))
class VarcharWriteState(val db: DBExecutor)
    extends WriteBenchmarkState[String](db)
    with TableVarcharSchema {

  import config.profile.api._

  //Overload: default constructor required by JMH. Uses Postgres as target DB.
  def this() = {
    this(DBExecutor.forPostgres(dbName, dbHost, dbPort))
  }

  def generateData(): String =
    Hash.generate.toHexString

  def beforeAll(): Unit =
    db.runNow(
      //action = create a fresh table
      action  = tableVarcharQuery.schema.dropIfExists.andThen(tableVarcharQuery.schema.create),
      timeout = requestTimeout
    )
}

/**
  * JMH state for benchmarking reads to [[TableByteSchema]].
  *
  * @param testDataCount total number of rows to generate in [[TableByteSchema]]
  */
@State(Scope.Thread)
@SuppressWarnings(Array("org.wartremover.warts.Overloading"))
class ByteaReadState(testDataCount: Int, val db: DBExecutor)
    extends ReadBenchmarkState[Array[Byte]](testDataCount = testDataCount, db = db)
    with TableByteSchema {

  import config.profile.api._

  //Overload: default constructor required by JMH. Uses Postgres as target DB.
  def this() = {
    this(testDataCount = readDataCount, db = DBExecutor.forPostgres(dbName, dbHost, dbPort))
  }

  def generateData(): Array[Byte] =
    Hash.generate.bytes.toArray

  def persist(data: Array[Array[Byte]]): Unit = {
    //create a fresh table and insert the data
    val query =
      tableByteaQuery.schema.dropIfExists
        .andThen(tableByteaQuery.schema.create)
        .andThen(tableByteaQuery ++= data)

    val _ = db.runNow(
      action  = query,
      timeout = batchWriteTimeout //set this according to the value of testDataCount
    )
  }
}

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
    this(testDataCount = readDataCount, db = DBExecutor.forPostgres(dbName, dbHost, dbPort))
  }

  def generateData(): String =
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

/**
  * Implements all JMH functions executing benchmarks on Postgres.
  *
  * Prerequisite: Database set by [[dbName]] should exists.
  */
@Fork(value        = 1, warmups = 0)
@Warmup(iterations = 0)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Measurement(iterations = 1, time = 1, timeUnit = TimeUnit.MINUTES) //runs this benchmark for x minutes
@SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
class DBBenchmark {

  /**
    * Benchmarks writes to `varchar` column type in [[TableVarcharSchema]]
    *
    * @param state State of current iteration
    */
  @Benchmark
  def writeVarchar(state: VarcharWriteState): Unit = {
    import state.config.profile.api._
    val _ = state.db.runNow(state.tableVarcharQuery += state.next, requestTimeout)
  }

  /**
    * Benchmarks writes to `bytea` column type in [[TableByteSchema]]
    *
    * @param state State of current iteration
    */
  @Benchmark
  def writeBytea(state: ByteaWriteState): Unit = {
    import state.config.profile.api._
    val _ = state.db.runNow(state.tableByteaQuery += state.next, requestTimeout)
  }

  /**
    * Benchmarks reads to `varchar` column type in [[TableVarcharSchema]].
    *
    * @param state State of current iteration
    */
  @Benchmark
  def readVarchar(state: VarcharReadState): Unit = {
    import state.config.profile.api._
    val _ =
      state.db.runNow(state.tableVarcharQuery.filter(_.hash === state.next).result, requestTimeout)
  }

  /**
    * Benchmarks reads to `bytea` column type in [[TableByteSchema]].
    *
    * @param state State of current iteration
    */
  @Benchmark
  def readBytea(state: ByteaReadState): Unit = {
    import state.config.profile.api._
    val _ =
      state.db.runNow(state.tableByteaQuery.filter(_.hash === state.next).result, requestTimeout)
  }

}
