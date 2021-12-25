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

package org.alephium.explorer.benchmark.postgres

import java.util.concurrent.TimeUnit

import scala.concurrent.Await
import scala.concurrent.duration._

import org.openjdk.jmh.annotations._
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.Hash
import org.alephium.explorer.benchmark.CurrentThreadExecutionContext.self
import org.alephium.explorer.benchmark.postgres.Config._
import org.alephium.explorer.benchmark.postgres.table.TableBytea
import org.alephium.explorer.benchmark.postgres.table.TableVarchar

//Default config
object Config {
  //sets the total number of rows to generate for read
  val defaultTestDataCount: Int      = 1000000
  val requestTimeout: FiniteDuration = 1.second
}

/**
  * JMH state for benchmarking writes to [[TableBytea]].
  */
@State(Scope.Thread)
class ByteaWriteBenchState
    extends WriteBenchStateBase[Array[Byte], TableBytea](() => Hash.generate.bytes.toArray,
                                                         TableBytea.tableQuery)

/**
  * JMH state for benchmarking [[TableVarchar]].
  */
@State(Scope.Thread)
class VarcharWriteBenchState
    extends WriteBenchStateBase[String, TableVarchar](() => Hash.generate.toHexString,
                                                      TableVarchar.tableQuery)

/**
  * JMH state for benchmarking reads to [[TableBytea]].
  *
  * @param testDataCount total number of rows to generate in [[TableBytea]]
  */
@State(Scope.Thread)
@SuppressWarnings(Array("org.wartremover.warts.Overloading"))
class ByteaReadBenchState(testDataCount: Int)
    extends ReadBenchStateBase[Array[Byte], TableBytea](() => Hash.generate.bytes.toArray,
                                                        TableBytea.tableQuery,
                                                        testDataCount) {
  //Overload: default constructor required by JMH
  def this() = {
    this(Config.defaultTestDataCount)
  }
}

/**
  * JMH state for benchmarking reads to [[TableVarchar]].
  *
  * @param testDataCount total number of rows to generate in [[TableVarchar]]
  */
@State(Scope.Thread)
@SuppressWarnings(Array("org.wartremover.warts.Overloading"))
class VarcharReadBenchState(testDataCount: Int)
    extends ReadBenchStateBase[String, TableVarchar](() => Hash.generate.toHexString,
                                                     TableVarchar.tableQuery,
                                                     testDataCount) {
  //Overload: default constructor required by JMH
  def this() = {
    this(Config.defaultTestDataCount)
  }
}

/**
  * Implements all JMH functions executing benchmarks on Postgres.
  */
@Fork(value        = 1, warmups = 0)
@Warmup(iterations = 0)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Measurement(iterations = 1, time = 5, timeUnit = TimeUnit.MINUTES) //runs this benchmark for x minutes
@SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
class PostgresBench {

  /**
    * Benchmarks writes to PostgreSQL's `varchar` column type in [[TableVarchar]]
    *
    * @param state State of current iteration
    */
  @Benchmark
  def writeVarcharBench(state: VarcharWriteBenchState): Unit =
    Await.result(
      state.connection.run(state.table += state.next),
      requestTimeout
    ): Unit

  /**
    * Benchmarks writes to PostgreSQL's `bytea` column type in [[TableBytea]]
    *
    * @param state State of current iteration
    */
  @Benchmark
  def writeByteaBench(state: ByteaWriteBenchState): Unit =
    Await.result(
      state.connection.run(state.table += state.next),
      requestTimeout
    ): Unit

  /**
    * Benchmarks reads to PostgreSQL's `varchar` column type in [[TableVarchar]].
    *
    * @param state State of current iteration
    */
  @Benchmark
  def readVarcharBench(state: VarcharReadBenchState): Unit =
    Await.result(
      state.connection.run(state.table.filter(_.hash === state.next).result),
      requestTimeout
    ): Unit

  /**
    * Benchmarks reads to PostgreSQL's `bytea` column type in [[TableBytea]].
    *
    * @param state State of current iteration
    */
  @Benchmark
  def readByteaBench(state: ByteaReadBenchState): Unit =
    Await.result(
      state.connection.run(state.table.filter(_.hash === state.next).result),
      requestTimeout
    ): Unit

}
