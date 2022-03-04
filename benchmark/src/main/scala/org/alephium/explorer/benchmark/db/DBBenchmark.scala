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

import scala.concurrent.{Await, ExecutionContext}

import org.openjdk.jmh.annotations._
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.benchmark.db.BenchmarkSettings._
import org.alephium.explorer.benchmark.db.state._
import org.alephium.explorer.persistence.queries.InputQueries._
import org.alephium.explorer.persistence.queries.OutputQueries._

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
// scalastyle:off number.of.methods
class DBBenchmark {

  /**
    * Benchmarks writes to `varchar` column type in [[org.alephium.explorer.benchmark.db.table.TableVarcharSchema]]
    *
    * @param state State of current iteration
    */
  @Benchmark
  def writeVarchar(state: VarcharWriteState): Unit = {
    val _ = state.db.runNow(state.tableVarcharQuery += state.next, requestTimeout)
  }

  /**
    * Benchmarks writes to `bytea` column type in [[org.alephium.explorer.benchmark.db.table.TableByteSchema]]
    *
    * @param state State of current iteration
    */
  @Benchmark
  def writeBytea(state: ByteaWriteState): Unit = {
    val _ = state.db.runNow(state.tableByteaQuery += state.next, requestTimeout)
  }

  /**
    * Benchmarks reads to `varchar` column type in [[org.alephium.explorer.benchmark.db.table.TableVarcharSchema]].
    *
    * @param state State of current iteration
    */
  @Benchmark
  def readVarchar(state: VarcharReadState): Unit = {
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
    val _ =
      state.db.runNow(state.tableByteaQuery.filter(_.hash === state.next).result, requestTimeout)
  }

  @Benchmark
  def readMainChainIndex(state: BlockHeaderWithMainChainReadState): Unit = {
    val _ =
      state.db.runNow(state.blockHeadersTable.filter(_.mainChain).length.result, requestTimeout)
  }

  @Benchmark
  def readNoMainChainIndex(state: BlockHeaderWithoutMainChainReadState): Unit = {
    val _ =
      state.db.runNow(state.blockHeadersTable.filter(_.mainChain).length.result, requestTimeout)
  }

  /**
    * CONNECTION POOL = DISABLED
    *
    * The following benchmarks listMainChain's forward & reverse queries with connection pool disabled
    */
  @Benchmark
  def listBlocks_Forward_DisabledCP_Typed(state: ListBlocks_Forward_DisabledCP_ReadState): Unit = {
    val _ =
      Await.result(state.dao.listMainChain(state.next), requestTimeout)
  }

  @Benchmark
  def listBlocks_Reverse_DisabledCP_Typed(state: ListBlocks_Reverse_DisabledCP_ReadState): Unit = {
    val _ =
      Await.result(state.dao.listMainChain(state.next), requestTimeout)
  }

  @Benchmark
  def listBlocks_Forward_DisabledCP_SQL(state: ListBlocks_Forward_DisabledCP_ReadState): Unit = {
    val _ =
      Await.result(state.dao.listMainChainSQL(state.next), requestTimeout)
  }

  @Benchmark
  def listBlocks_Reverse_DisabledCP_SQL(state: ListBlocks_Reverse_DisabledCP_ReadState): Unit = {
    val _ =
      Await.result(state.dao.listMainChainSQL(state.next), requestTimeout)
  }

  /**
    * CONNECTION POOL = HIKARI
    *
    * Benchmarks listMainChain forward & reverse queries with [[DBConnectionPool.HikariCP]] as
    * the connection pool
    */
  @Benchmark
  def listBlocks_Forward_HikariCP_Typed(state: ListBlocks_Forward_HikariCP_ReadState): Unit = {
    val _ =
      Await.result(state.dao.listMainChain(state.next), requestTimeout)
  }

  @Benchmark
  def listBlocks_Forward_HikariCP_SQL(state: ListBlocks_Forward_HikariCP_ReadState): Unit = {
    val _ =
      Await.result(state.dao.listMainChainSQL(state.next), requestTimeout)
  }

  /**
    * Address benchmarks
    */
  @Benchmark
  def getBalanceQuery(state: Address_ReadState): Unit = {
    val _ =
      state.db.runNow(state.queries.getBalanceQuery(state.address).result, requestTimeout)
  }

  @Benchmark
  def getBalanceSQL(state: Address_ReadState): Unit = {
    val _ =
      state.db.runNow(state.queries.getBalanceQuerySQL(state.address), requestTimeout)
  }

  @Benchmark
  def getTxHashesByAddressQuery(state: Address_ReadState): Unit = {
    val _ =
      state.db.runNow(
        state.queries
          .getTxHashesByAddressQuery(
            (state.address, state.pagination.offset.toLong, state.pagination.limit.toLong))
          .result,
        requestTimeout)
  }

  @Benchmark
  def getTxHashesByAddressQuerySQL(state: Address_ReadState): Unit = {
    val _ =
      state.db.runNow(state.queries.getTxHashesByAddressQuerySQL(state.address,
                                                                 state.pagination.offset,
                                                                 state.pagination.limit),
                      requestTimeout)
  }

  @Benchmark
  def getTxHashesByAddressQuerySQLNoJoin(state: Address_ReadState): Unit = {
    val _ =
      state.db.runNow(state.queries.getTxHashesByAddressQuerySQLNoJoin(state.address,
                                                                       state.pagination.offset,
                                                                       state.pagination.limit),
                      requestTimeout)
  }

  @Benchmark
  def countAddressTransactionsSQL(state: Address_ReadState): Unit = {
    val _ =
      state.db.runNow(state.queries.countAddressTransactionsSQL(state.address), requestTimeout)
  }

  @Benchmark
  def countAddressTransactionsSQLNoJoin(state: Address_ReadState): Unit = {
    val _ =
      state.db
        .runNow(state.queries.countAddressTransactionsSQLNoJoin(state.address), requestTimeout)
  }

  @Benchmark
  def getAddressInfo(state: Address_ReadState): Unit = {
    implicit val ec: ExecutionContext = ExecutionContext.global

    val _ = Await.result(for {
      _ <- state.dao.getBalanceSQL(state.address)
      _ <- state.dao.getNumberByAddressSQL(state.address)
    } yield (), requestTimeout)
  }

  @Benchmark
  def getAddressInfoWithTxAddressTable(state: Address_ReadState): Unit = {
    implicit val ec: ExecutionContext = ExecutionContext.global

    val _ = Await.result(for {
      _ <- state.dao.getBalanceSQL(state.address)
      _ <- state.dao.getNumberByAddressSQLNoJoin(state.address)
    } yield (), requestTimeout)
  }

  @Benchmark
  def getInputsFromTxs(state: Address_ReadState): Unit = {
    val _ =
      state.db.runNow(inputsFromTxs(state.txHashes).result, requestTimeout)
  }

  @Benchmark
  def getInputsFromTxsSQL(state: Address_ReadState): Unit = {
    val _ =
      state.db.runNow(inputsFromTxsSQL(state.txHashes), requestTimeout)
  }

  @Benchmark
  def getOutputsFromTxs(state: Address_ReadState): Unit = {
    val _ =
      state.db.runNow(outputsFromTxs(state.txHashes).result, requestTimeout)
  }

  @Benchmark
  def getOutputsFromTxsSQL(state: Address_ReadState): Unit = {
    val _ =
      state.db.runNow(outputsFromTxsSQL(state.txHashes), requestTimeout)
  }

  @Benchmark
  def getGasFromTxs(state: Address_ReadState): Unit = {
    val _ =
      state.db.runNow(state.queries.gasFromTxs(state.txHashes).result, requestTimeout)
  }

  @Benchmark
  def getGasFromTxsSQL(state: Address_ReadState): Unit = {
    val _ =
      state.db.runNow(state.queries.gasFromTxsSQL(state.txHashes), requestTimeout)
  }

  @Benchmark
  def getTransactionsByAddress(state: Address_ReadState): Unit = {
    val _ =
      state.db.runNow(state.queries.getTransactionsByAddress(state.address, state.pagination),
                      requestTimeout)
  }

  @Benchmark
  def getTransactionsByAddressSQL(state: Address_ReadState): Unit = {
    val _ =
      state.db.runNow(state.queries.getTransactionsByAddressSQL(state.address, state.pagination),
                      requestTimeout)
  }

  /** Benchmarks for inserting Blocks. With & without HikariCP */

  @Benchmark
  def blockEntityWrite_DisabledCP(state: BlockEntityWriteState_DisabledCP): Unit = {
    val _ =
      Await.result(state.dao.insert(state.next), requestTimeout)
  }

  @Benchmark
  def blockEntityWrite_HikariCP(state: BlockEntityWriteState_HikariCP): Unit = {
    val _ =
      Await.result(state.dao.insert(state.next), requestTimeout)
  }
}
