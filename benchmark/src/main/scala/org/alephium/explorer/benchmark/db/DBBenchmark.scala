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

import org.alephium.explorer.benchmark.db.BenchmarkSettings._
import org.alephium.explorer.benchmark.db.state._

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
    * Benchmarks writes to `varchar` column type in [[org.alephium.explorer.benchmark.db.table.TableVarcharSchema]]
    *
    * @param state State of current iteration
    */
  @Benchmark
  def writeVarchar(state: VarcharWriteState): Unit = {
    import state.config.profile.api._
    val _ = state.db.runNow(state.tableVarcharQuery += state.next, requestTimeout)
  }

  /**
    * Benchmarks writes to `bytea` column type in [[org.alephium.explorer.benchmark.db.table.TableByteSchema]]
    *
    * @param state State of current iteration
    */
  @Benchmark
  def writeBytea(state: ByteaWriteState): Unit = {
    import state.config.profile.api._
    val _ = state.db.runNow(state.tableByteaQuery += state.next, requestTimeout)
  }

  /**
    * Benchmarks reads to `varchar` column type in [[org.alephium.explorer.benchmark.db.table.TableVarcharSchema]].
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
    * Benchmarks reads to `bytea` column type in [[org.alephium.explorer.benchmark.db.table.TableByteSchema]].
    *
    * @param state State of current iteration
    */
  @Benchmark
  def readBytea(state: ByteaReadState): Unit = {
    import state.config.profile.api._
    val _ =
      state.db.runNow(state.tableByteaQuery.filter(_.hash === state.next).result, requestTimeout)
  }

  @Benchmark
  def readWithMainChainIndex(state: BlockHeaderWithMainChainReadState): Unit = {
    import state.config.profile.api._
    val _ =
      state.db.runNow(state.blockHeadersTable.filter(_.mainChain).length.result, requestTimeout)
  }

  @Benchmark
  def readWithoutMainChainIndex(state: BlockHeaderWithoutMainChainReadState): Unit = {
    import state.config.profile.api._
    val _ =
      state.db.runNow(state.blockHeadersTable.filter(_.mainChain).length.result, requestTimeout)
  }

}
