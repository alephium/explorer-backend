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

import scala.concurrent.Await

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
@Measurement(iterations = 1, time = 5, timeUnit = TimeUnit.SECONDS) //runs this benchmark for x minutes
@SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
class DBBenchmark {

//   /**
//     * Benchmarks writes to `varchar` column type in [[org.alephium.explorer.benchmark.db.table.TableVarcharSchema]]
//     *
//     * @param state State of current iteration
//     */
//   @Benchmark
//   def writeVarchar(state: VarcharWriteState): Unit = {
//     import state.config.profile.api._
//     val _ = state.db.runNow(state.tableVarcharQuery += state.next, requestTimeout)
//   }

//   /**
//     * Benchmarks writes to `bytea` column type in [[org.alephium.explorer.benchmark.db.table.TableByteSchema]]
//     *
//     * @param state State of current iteration
//     */
//   @Benchmark
//   def writeBytea(state: ByteaWriteState): Unit = {
//     import state.config.profile.api._
//     val _ = state.db.runNow(state.tableByteaQuery += state.next, requestTimeout)
//   }

//   /**
//     * Benchmarks reads to `varchar` column type in [[org.alephium.explorer.benchmark.db.table.TableVarcharSchema]].
//     *
//     * @param state State of current iteration
//     */
//   @Benchmark
//   def readVarchar(state: VarcharReadState): Unit = {
//     import state.config.profile.api._
//     val _ =
//       state.db.runNow(state.tableVarcharQuery.filter(_.hash === state.next).result, requestTimeout)
//   }

//   /**
//     * Benchmarks reads to `bytea` column type in [[org.alephium.explorer.benchmark.db.table.TableByteSchema]].
//     *
//     * @param state State of current iteration
//     */
//   @Benchmark
//   def readBytea(state: ByteaReadState): Unit = {
//     import state.config.profile.api._
//     val _ =
//       state.db.runNow(state.tableByteaQuery.filter(_.hash === state.next).result, requestTimeout)
//   }

//   @Benchmark
//   def readMainChainIndex(state: BlockHeaderWithMainChainReadState): Unit = {
//     import state.config.profile.api._
//     val _ =
//       state.db.runNow(state.blockHeadersTable.filter(_.mainChain).length.result, requestTimeout)
//   }

//   @Benchmark
//   def readNoMainChainIndex(state: BlockHeaderWithoutMainChainReadState): Unit = {
//     import state.config.profile.api._
//     val _ =
//       state.db.runNow(state.blockHeadersTable.filter(_.mainChain).length.result, requestTimeout)
//   }

//   /**
//     * CONNECTION POOL = DISABLED
//     *
//     * The following benchmarks listMainChain's forward & reverse queries with connection pool disabled
//     */
//   @Benchmark
//   def listBlocks_Forward_DisabledCP_Typed(state: ListBlocks_Forward_DisabledCP_ReadState): Unit = {
//     val _ =
//       Await.result(state.dao.listMainChain(state.next), requestTimeout)
//   }

//   @Benchmark
//   def listBlocks_Reverse_DisabledCP_Typed(state: ListBlocks_Reverse_DisabledCP_ReadState): Unit = {
//     val _ =
//       Await.result(state.dao.listMainChain(state.next), requestTimeout)
//   }

//   @Benchmark
//   def listBlocks_Forward_DisabledCP_SQL(state: ListBlocks_Forward_DisabledCP_ReadState): Unit = {
//     val _ =
//       Await.result(state.dao.listMainChainSQL(state.next), requestTimeout)
//   }

//   @Benchmark
//   def listBlocks_Reverse_DisabledCP_SQL(state: ListBlocks_Reverse_DisabledCP_ReadState): Unit = {
//     val _ =
//       Await.result(state.dao.listMainChainSQL(state.next), requestTimeout)
//   }

//   /**
//     * CONNECTION POOL = HIKARI
//     *
//     * Benchmarks listMainChain forward & reverse queries with [[DBConnectionPool.HikariCP]] as
//     * the connection pool
//     */
//   @Benchmark
//   def listBlocks_Forward_HikariCP_Typed(state: ListBlocks_Forward_HikariCP_ReadState): Unit = {
//     val _ =
//       Await.result(state.dao.listMainChain(state.next), requestTimeout)
//   }

//   @Benchmark
//   def listBlocks_Forward_HikariCP_SQL(state: ListBlocks_Forward_HikariCP_ReadState): Unit = {
//     val _ =
//       Await.result(state.dao.listMainChainSQL(state.next), requestTimeout)
//   }

  /**
   * Address benchmarks
   */
  // @Benchmark
  // def getBalanceQuery(state: Address_ReadState): Unit = {
  //   import state.db.config.profile.api._
  //   val _  =
  //     state.db.runNow(state.queries.getBalanceQuery(state.address).result, requestTimeout)
  // }

  // @Benchmark
  // def getBalanceSQL(state: Address_ReadState): Unit = {
  //   val _  =
  //     state.db.runNow(state.queries.getUnlockedBalanceSQL(state.address), requestTimeout)
  // }

  // @Benchmark
  // def getBalanceSQLNoJoin(state: Address_ReadState): Unit = {
  //   val _  =
  //     state.db.runNow(state.queries.getUnlockedBalanceSQLNoJoin(state.address), requestTimeout)
  // }

  // @Benchmark
  // def getTxHashesByAddressQuery(state: Address_ReadState): Unit = {
  //   import state.db.config.profile.api._
  //   val _  =
  //     state.db.runNow(state.queries.getTxHashesByAddressQuery((state.address, state.pagination.offset, state.pagination.limit)).result, requestTimeout)
  // }

  // @Benchmark
  // def getTxHashesByAddressQuerySQL(state: Address_ReadState): Unit = {
  //   val _  =
  //     state.db.runNow(state.queries.getTxHashesByAddressQuerySQL(state.address, state.pagination.offset, state.pagination.limit), requestTimeout)
  // }

  // @Benchmark
  // def getTxHashesByAddressQuerySQLNoJoin(state: Address_ReadState): Unit = {
  //   val _  =
  //     state.db.runNow(state.queries.getTxHashesByAddressQuerySQLNoJoin(state.address, state.pagination.offset, state.pagination.limit), requestTimeout)
  // }

  // @Benchmark
  // def countAddressTransactionsSQL(state: Address_ReadState): Unit = {
  //   val _  =
  //     state.db.runNow(state.queries.countAddressTransactionsSQL(state.address), requestTimeout)
  // }

  // @Benchmark
  // def countAddressTransactionsSQLNoJoin(state: Address_ReadState): Unit = {
  //   val _  =
  //     state.db.runNow(state.queries.countAddressTransactionsSQLNoJoin(state.address), requestTimeout)
  // }

  @Benchmark
  def getInputsFromTxs(state: Address_ReadState): Unit = {
     import state.config.profile.api._
    val _  =
      state.db.runNow(state.queries.inputsFromTxs(state.blocks.flatMap(_.transactions.map(_.hash))).result, requestTimeout)
  }

  @Benchmark
  def getInputsFromTxsSQL(state: Address_ReadState): Unit = {
     import state.config.profile.api._
    val _  =
      state.db.runNow(state.queries.inputsFromTxsSQL(state.blocks.flatMap(_.transactions.map(_.hash))), requestTimeout)
  }

  @Benchmark
  def getInputsFromTxsNoJoin(state: Address_ReadState): Unit = {
     import state.config.profile.api._
    val _  =
      state.db.runNow(state.queries.inputsFromTxsNoJoin(state.blocks.flatMap(_.transactions.map(_.hash))).result, requestTimeout)
  }

  @Benchmark
  def getInputsFromTxsSQLNoJoin(state: Address_ReadState): Unit = {
     import state.config.profile.api._
    val _  =
      state.db.runNow(state.queries.inputsFromTxsSQLNoJoin(state.blocks.flatMap(_.transactions.map(_.hash))), requestTimeout)
  }
}
