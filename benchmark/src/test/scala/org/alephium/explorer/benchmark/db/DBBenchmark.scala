// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.benchmark.db

import java.util.concurrent.TimeUnit

import scala.concurrent.{Await, ExecutionContext}

import org.openjdk.jmh.annotations._
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._

import org.alephium.api.model.{Address => ApiAddress}
import org.alephium.explorer.GenApiModel._
import org.alephium.explorer.GroupSetting
import org.alephium.explorer.api.model.{IntervalType, Pagination}
import org.alephium.explorer.benchmark.db.BenchmarkSettings._
import org.alephium.explorer.benchmark.db.state._
import org.alephium.explorer.cache.BlockCache
import org.alephium.explorer.config.Default.groupConfig
import org.alephium.explorer.persistence.dao.{BlockDao, TransactionDao}
import org.alephium.explorer.persistence.queries.InputQueries._
import org.alephium.explorer.persistence.queries.OutputQueries._
import org.alephium.explorer.persistence.queries.TransactionQueries
import org.alephium.explorer.persistence.schema.BlockHeaderSchema
import org.alephium.explorer.service.TransactionService
import org.alephium.util.{Duration, TimeStamp}

/** Implements all JMH functions executing benchmarks on Postgres.
  *
  * Prerequisite: Database set by [[org.alephium.explorer.benchmark.db.BenchmarkSettings.dbName]]
  * should exists.
  */
@Fork(value = 1, warmups = 0)
@Warmup(iterations = 0)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Measurement(
  iterations = 1,
  time = 5,
  timeUnit = TimeUnit.SECONDS
) //runs this benchmark for x minutes
@SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
// scalastyle:off number.of.methods
class DBBenchmark {

  /** Benchmarks writes to `varchar` column type in
    * [[org.alephium.explorer.benchmark.db.table.TableVarcharSchema]]
    *
    * @param state
    *   State of current iteration
    */
  @Benchmark
  def writeVarchar(state: VarcharWriteState): Unit = {
    val _ = state.db.runNow(state.tableVarcharQuery += state.next, requestTimeout)
  }

  /** Benchmarks writes to `bytea` column type in
    * [[org.alephium.explorer.benchmark.db.table.TableByteSchema]]
    *
    * @param state
    *   State of current iteration
    */
  @Benchmark
  def writeBytea(state: ByteaWriteState): Unit = {
    val _ = state.db.runNow(state.tableByteaQuery += state.next, requestTimeout)
  }

  /** Benchmarks reads to `varchar` column type in
    * [[org.alephium.explorer.benchmark.db.table.TableVarcharSchema]].
    *
    * @param state
    *   State of current iteration
    */
  @Benchmark
  def readVarchar(state: VarcharReadState): Unit = {
    val _ =
      state.db.runNow(state.tableVarcharQuery.filter(_.hash === state.next).result, requestTimeout)
  }

  /** Benchmarks reads to `bytea` column type in
    * [[org.alephium.explorer.benchmark.db.table.TableByteSchema]].
    *
    * @param state
    *   State of current iteration
    */
  @Benchmark
  def readBytea(state: ByteaReadState): Unit = {
    val _ =
      state.db.runNow(state.tableByteaQuery.filter(_.hash === state.next).result, requestTimeout)
  }

  @Benchmark
  def readMainChainIndex(state: BlockHeaderWithMainChainReadState): Unit = {
    val _ =
      state.db.runNow(BlockHeaderSchema.table.filter(_.mainChain).length.result, requestTimeout)
  }

  @Benchmark
  def readNoMainChainIndex(state: BlockHeaderWithoutMainChainReadState): Unit = {
    val _ =
      state.db.runNow(BlockHeaderSchema.table.filter(_.mainChain).length.result, requestTimeout)
  }

  /** CONNECTION POOL = DISABLED
    *
    * The following benchmarks listMainChain's forward & reverse queries with connection pool
    * disabled
    */
  @Benchmark
  def listBlocks_Forward_DisabledCP_SQL(state: ListBlocks_Forward_DisabledCP_ReadState): Unit = {
    implicit val ec: ExecutionContext                = state.config.db.ioExecutionContext
    implicit val dc: DatabaseConfig[PostgresProfile] = state.config
    implicit val cache: BlockCache                   = state.blockCache

    val _ =
      Await.result(BlockDao.listMainChain(state.next), requestTimeout)
  }

  @Benchmark
  def listBlocks_Reverse_DisabledCP_SQL(state: ListBlocks_Reverse_DisabledCP_ReadState): Unit = {
    implicit val ec: ExecutionContext                = state.config.db.ioExecutionContext
    implicit val dc: DatabaseConfig[PostgresProfile] = state.config
    implicit val cache: BlockCache                   = state.blockCache

    val _ =
      Await.result(BlockDao.listMainChain(state.next), requestTimeout)
  }

  /** CONNECTION POOL = HIKARI
    *
    * Benchmarks listMainChain forward & reverse queries with [[DBConnectionPool.HikariCP]] as the
    * connection pool
    */
  @Benchmark
  def listBlocks_Forward_HikariCP_SQL_Cached(state: ListBlocks_Forward_HikariCP_ReadState): Unit = {
    implicit val ec: ExecutionContext                = state.config.db.ioExecutionContext
    implicit val dc: DatabaseConfig[PostgresProfile] = state.config
    implicit val cache: BlockCache                   = state.blockCache

    val _ =
      Await.result(BlockDao.listMainChain(state.next), requestTimeout)
  }

  /** Address benchmarks
    */

  @Benchmark
  def getBalance(state: Address_ReadState): Unit = {
    import state.executionContext

    val _ =
      state.db.runNow(
        TransactionQueries.getBalanceAction(state.address, TimeStamp.zero),
        requestTimeout
      )
  }

  @Benchmark
  def getTxHashesByAddressQuery(state: Address_ReadState): Unit = {
    val _ =
      state.db.runNow(
        TransactionQueries.getTxHashesByAddressQuery(state.address, state.pagination),
        requestTimeout
      )
  }

  @Benchmark
  def countAddressTransactions(state: Address_ReadState): Unit = {
    val _ =
      state.db
        .runNow(TransactionQueries.countAddressTransactions(state.address), requestTimeout)
  }

  @Benchmark
  def getAddressInfoWithTxAddressTable(state: Address_ReadState): Unit = {
    import state.{databaseConfig, executionContext}

    val _ = Await.result(
      for {
        _ <- TransactionDao.getBalance(state.address, TimeStamp.zero)
        _ <- TransactionDao.getNumberByAddress(state.address)
      } yield (),
      requestTimeout
    )
  }

  @Benchmark
  def getInputsFromTxs(state: Address_ReadState): Unit = {
    val _ =
      state.db.runNow(inputsFromTxs(state.hashes), requestTimeout)
  }

  @Benchmark
  def getOutputsFromTxs(state: Address_ReadState): Unit = {
    val _ =
      state.db.runNow(outputsFromTxs(state.hashes), requestTimeout)
  }

  @Benchmark
  def getGasFromTxs(state: Address_ReadState): Unit = {
    val _ =
      state.db.runNow(TransactionQueries.infoFromTxs(state.hashes), requestTimeout)
  }

  @Benchmark
  def getTransactionsByAddress(state: Address_ReadState): Unit = {
    import state.executionContext

    val _ =
      state.db.runNow(
        TransactionQueries.getTransactionsByAddress(state.address, state.pagination),
        requestTimeout
      )
  }

  /** Benchmarks for inserting Blocks. With & without HikariCP */

  @Benchmark
  def blockEntityWrite_DisabledCP(state: BlockEntityWriteState_DisabledCP): Unit = {
    implicit val ec: ExecutionContext                = state.config.db.ioExecutionContext
    implicit val dc: DatabaseConfig[PostgresProfile] = state.config
    implicit val groupSetting: GroupSetting          = state.groupSetting

    val _ =
      Await.result(BlockDao.insert(state.next), requestTimeout)
  }

  @Benchmark
  def blockEntityWrite_HikariCP(state: BlockEntityWriteState_HikariCP): Unit = {
    implicit val ec: ExecutionContext                = state.config.db.ioExecutionContext
    implicit val dc: DatabaseConfig[PostgresProfile] = state.config
    implicit val groupSetting: GroupSetting          = state.groupSetting

    val _ =
      Await.result(BlockDao.insert(state.next), requestTimeout)
  }

  @Benchmark
  def areAddressesActiveAction_DisabledCP(state: AreAddressesActiveReadState_DisabledCP): Unit = {
    implicit val ec: ExecutionContext = state.config.db.ioExecutionContext

    val _ =
      state.db.runNow(
        TransactionQueries.areAddressesActiveAction(
          state.next.map(address => ApiAddress.from(address.lockupScript))
        ),
        requestTimeout
      )
  }

  @Benchmark
  def areAddressesActiveAction_HikariCP(state: AreAddressesActiveReadState_HikariCP): Unit = {
    implicit val ec: ExecutionContext = state.config.db.ioExecutionContext

    val _ =
      state.db.runNow(
        TransactionQueries.areAddressesActiveAction(
          state.next.map(address => ApiAddress.from(address.lockupScript))
        ),
        requestTimeout
      )
  }

  @Benchmark
  def transactions_per_address_read_state(state: TransactionsPerAddressReadState): Unit = {
    val query =
      TransactionQueries.getTxHashesByAddressQueryTimeRanged(
        address = ApiAddress.fromBase58(state.next).toOption.get,
        fromTime = TimeStamp.zero,
        toTime = TimeStamp.unsafe(Long.MaxValue),
        pagination = Pagination.unsafe(1, Int.MaxValue)
      )

    val _ =
      Await.result(state.config.db.run(query), requestTimeout)
  }

  @Benchmark
  def getAmountHistory(state: Address_ReadState): Unit = {
    implicit val ec: ExecutionContext                = state.config.db.ioExecutionContext
    implicit val dc: DatabaseConfig[PostgresProfile] = state.config
    val timestamps                                   = state.blocks.map(_.timestamp)
    val from                                         = timestamps.min
    val to           = from.plusMillisUnsafe(Duration.ofDaysUnsafe(366L).millis)
    val intervalType = IntervalType.Daily

    val res = TransactionService
      .getAmountHistory(state.address, from, to, intervalType)

    val _ =
      Await.result(res, requestTimeout)
  }
}
