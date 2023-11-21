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

import scala.concurrent.{Await, ExecutionContext, Future}

import scala.collection.immutable.ArraySeq
import org.openjdk.jmh.annotations._
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.{foldFutures, GroupSetting}
import org.alephium.explorer.service.TransactionService
import org.alephium.explorer.GroupSetting
import org.alephium.explorer.api.model.{IntervalType, Pagination}
import org.alephium.explorer.benchmark.db.BenchmarkSettings._
import org.alephium.explorer.benchmark.db.state._
import org.alephium.explorer.cache.BlockCache
import org.alephium.explorer.persistence.dao.{BlockDao, TransactionDao}
import org.alephium.explorer.persistence.queries.InputQueries._
import org.alephium.explorer.persistence.queries.OutputQueries._
import org.alephium.explorer.persistence.queries.TransactionQueries
import org.alephium.explorer.persistence.schema.BlockHeaderSchema
import org.alephium.explorer.util.TimeUtil
import org.alephium.protocol.model.Address
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

//   /** Benchmarks writes to `varchar` column type in
//     * [[org.alephium.explorer.benchmark.db.table.TableVarcharSchema]]
//     *
//     * @param state
//     *   State of current iteration
//     */
//   @Benchmark
//   def writeVarchar(state: VarcharWriteState): Unit = {
//     val _ = state.db.runNow(state.tableVarcharQuery += state.next, requestTimeout)
//   }

//   /** Benchmarks writes to `bytea` column type in
//     * [[org.alephium.explorer.benchmark.db.table.TableByteSchema]]
//     *
//     * @param state
//     *   State of current iteration
//     */
//   @Benchmark
//   def writeBytea(state: ByteaWriteState): Unit = {
//     val _ = state.db.runNow(state.tableByteaQuery += state.next, requestTimeout)
//   }

//   /** Benchmarks reads to `varchar` column type in
//     * [[org.alephium.explorer.benchmark.db.table.TableVarcharSchema]].
//     *
//     * @param state
//     *   State of current iteration
//     */
//   @Benchmark
//   def readVarchar(state: VarcharReadState): Unit = {
//     val _ =
//       state.db.runNow(state.tableVarcharQuery.filter(_.hash === state.next).result, requestTimeout)
//   }

//   /** Benchmarks reads to `bytea` column type in
//     * [[org.alephium.explorer.benchmark.db.table.TableByteSchema]].
//     *
//     * @param state
//     *   State of current iteration
//     */
//   @Benchmark
//   def readBytea(state: ByteaReadState): Unit = {
//     val _ =
//       state.db.runNow(state.tableByteaQuery.filter(_.hash === state.next).result, requestTimeout)
//   }

//   @Benchmark
//   def readMainChainIndex(state: BlockHeaderWithMainChainReadState): Unit = {
//     val _ =
//       state.db.runNow(BlockHeaderSchema.table.filter(_.mainChain).length.result, requestTimeout)
//   }

//   @Benchmark
//   def readNoMainChainIndex(state: BlockHeaderWithoutMainChainReadState): Unit = {
//     val _ =
//       state.db.runNow(BlockHeaderSchema.table.filter(_.mainChain).length.result, requestTimeout)
//   }

//   /** CONNECTION POOL = DISABLED
//     *
//     * The following benchmarks listMainChain's forward & reverse queries with connection pool
//     * disabled
//     */
//   @Benchmark
//   def listBlocks_Forward_DisabledCP_SQL(state: ListBlocks_Forward_DisabledCP_ReadState): Unit = {
//     implicit val ec: ExecutionContext                = state.config.db.ioExecutionContext
//     implicit val dc: DatabaseConfig[PostgresProfile] = state.config
//     implicit val cache: BlockCache                   = state.blockCache

//     val _ =
//       Await.result(BlockDao.listMainChain(state.next), requestTimeout)
//   }

//   @Benchmark
//   def listBlocks_Reverse_DisabledCP_SQL(state: ListBlocks_Reverse_DisabledCP_ReadState): Unit = {
//     implicit val ec: ExecutionContext                = state.config.db.ioExecutionContext
//     implicit val dc: DatabaseConfig[PostgresProfile] = state.config
//     implicit val cache: BlockCache                   = state.blockCache

//     val _ =
//       Await.result(BlockDao.listMainChain(state.next), requestTimeout)
//   }

//   /** CONNECTION POOL = HIKARI
//     *
//     * Benchmarks listMainChain forward & reverse queries with [[DBConnectionPool.HikariCP]] as the
//     * connection pool
//     */
//   @Benchmark
//   def listBlocks_Forward_HikariCP_SQL_Cached(state: ListBlocks_Forward_HikariCP_ReadState): Unit = {
//     implicit val ec: ExecutionContext                = state.config.db.ioExecutionContext
//     implicit val dc: DatabaseConfig[PostgresProfile] = state.config
//     implicit val cache: BlockCache                   = state.blockCache

//     val _ =
//       Await.result(BlockDao.listMainChain(state.next), requestTimeout)
//   }

//   /** Address benchmarks
//     */

//   @Benchmark
//   def getBalance(state: Address_ReadState): Unit = {
//     import state.executionContext

//     val _ =
//       state.db.runNow(TransactionQueries.getBalanceAction(state.address), requestTimeout)
//   }

//   @Benchmark
//   def getTxHashesByAddressQuery(state: Address_ReadState): Unit = {
//     val _ =
//       state.db.runNow(
//         TransactionQueries.getTxHashesByAddressQuery(state.address, state.pagination),
//         requestTimeout
//       )
//   }

//   @Benchmark
//   def countAddressTransactions(state: Address_ReadState): Unit = {
//     val _ =
//       state.db
//         .runNow(TransactionQueries.countAddressTransactions(state.address), requestTimeout)
//   }

//   @Benchmark
//   def getAddressInfoWithTxAddressTable(state: Address_ReadState): Unit = {
//     import state.{databaseConfig, executionContext}

//     val _ = Await.result(
//       for {
//         _ <- TransactionDao.getBalance(state.address)
//         _ <- TransactionDao.getNumberByAddress(state.address)
//       } yield (),
//       requestTimeout
//     )
//   }

//   @Benchmark
//   def getInputsFromTxs(state: Address_ReadState): Unit = {
//     val _ =
//       state.db.runNow(inputsFromTxs(state.hashes), requestTimeout)
//   }

//   @Benchmark
//   def getOutputsFromTxs(state: Address_ReadState): Unit = {
//     val _ =
//       state.db.runNow(outputsFromTxs(state.hashes), requestTimeout)
//   }

//   @Benchmark
//   def getGasFromTxs(state: Address_ReadState): Unit = {
//     val _ =
//       state.db.runNow(TransactionQueries.infoFromTxs(state.hashes), requestTimeout)
//   }

  // @Benchmark
  // def getTransactionsByAddress(state: Address_ReadState): Unit = {
  //   import state.executionContext

  //   val _ =
  //     state.db.runNow(
  //       TransactionQueries.getTransactionsByAddress(state.address, state.pagination),
  //       requestTimeout
  //     )
  // }

//   /** Benchmarks for inserting Blocks. With & without HikariCP */

//   @Benchmark
//   def blockEntityWrite_DisabledCP(state: BlockEntityWriteState_DisabledCP): Unit = {
//     implicit val ec: ExecutionContext                = state.config.db.ioExecutionContext
//     implicit val dc: DatabaseConfig[PostgresProfile] = state.config
//     implicit val groupSetting: GroupSetting          = state.groupSetting

//     val _ =
//       Await.result(BlockDao.insert(state.next), requestTimeout)
//   }

//   @Benchmark
//   def blockEntityWrite_HikariCP(state: BlockEntityWriteState_HikariCP): Unit = {
//     implicit val ec: ExecutionContext                = state.config.db.ioExecutionContext
//     implicit val dc: DatabaseConfig[PostgresProfile] = state.config
//     implicit val groupSetting: GroupSetting          = state.groupSetting

//     val _ =
//       Await.result(BlockDao.insert(state.next), requestTimeout)
//   }

//   @Benchmark
//   def areAddressesActiveAction_DisabledCP(state: AreAddressesActiveReadState_DisabledCP): Unit = {
//     implicit val ec: ExecutionContext = state.config.db.ioExecutionContext

//     val _ =
//       state.db.runNow(TransactionQueries.areAddressesActiveAction(state.next), requestTimeout)
//   }

//   @Benchmark
//   def areAddressesActiveAction_HikariCP(state: AreAddressesActiveReadState_HikariCP): Unit = {
//     implicit val ec: ExecutionContext = state.config.db.ioExecutionContext

//     val _ =
//       state.db.runNow(TransactionQueries.areAddressesActiveAction(state.next), requestTimeout)
//   }

//   @Benchmark
//   def transactions_per_address_read_state(state: TransactionsPerAddressReadState): Unit = {
//     val query =
//       TransactionQueries.getTxHashesByAddressQueryTimeRanged(
//         address = Address.fromBase58(state.next).get,
//         fromTime = TimeStamp.zero,
//         toTime = TimeStamp.unsafe(Long.MaxValue),
//         pagination = Pagination.unsafe(1, Int.MaxValue)
//       )

//     val _ =
//       Await.result(state.config.db.run(query), requestTimeout)
//   }

 @Benchmark
def sumAddrressOutputs(state: Address_ReadState): Unit = {
  implicit val ec: ExecutionContext = state.config.db.ioExecutionContext
     implicit val dc: DatabaseConfig[PostgresProfile] = state.config
  val timestamps = state.blocks.map(_.timestamp)
      val from = timestamps.min
      val to = from.plusMillisUnsafe(Duration.ofDaysUnsafe(365L).millis)
    val intervalType = IntervalType.Daily

      val flowable = TransactionService
        .getAmountHistory(state.address, from, to, intervalType, 8)

      val res =
        flowable.toList().blockingGet()
}

  @Benchmark
  def sumAddrressOutputs2(state: Address_ReadState): Unit = {
    implicit val ec: ExecutionContext = state.config.db.ioExecutionContext
     implicit val dc: DatabaseConfig[PostgresProfile] = state.config
    val timestamps = state.blocks.map(_.timestamp)
        val from = timestamps.min
        val to = from.plusMillisUnsafe(Duration.ofDaysUnsafe(365L).millis)
      val intervalType = IntervalType.Daily
      val res = TransactionService
        .getAmountHistory2(state.address, from, to, intervalType)

        val res2 =
      Await.result(res, requestTimeout)
  }
}
