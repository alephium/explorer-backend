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

import java.math.BigInteger

import scala.util.Random

import akka.util.ByteString
import org.openjdk.jmh.annotations.{Scope, State}

import org.alephium.crypto.Blake2b
import org.alephium.explorer.{BlockHash, Hash}
import org.alephium.explorer.api.model._
import org.alephium.explorer.benchmark.db.{DBConnectionPool, DBExecutor}
import org.alephium.explorer.benchmark.db.BenchmarkSettings._
import org.alephium.explorer.benchmark.db.state.ListBlocksReadStateSettings._
import org.alephium.explorer.persistence.dao.BlockDao
import org.alephium.explorer.persistence.model.{BlockHeader, TransactionEntity}
import org.alephium.explorer.persistence.schema.{BlockHeaderSchema, TransactionSchema}
import org.alephium.util.{TimeStamp, U256}

/**
  * JMH state for benchmarking reads to [[BlockHeaderSchema]] & [[TransactionSchema]].
  */
class ListBlocksReadState(reverse: Boolean,
                          maxPages: Int,
                          limitPerPage: Int,
                          transactionsPerBlock: Int,
                          val db: DBExecutor)
    extends ReadBenchmarkState[Pagination](testDataCount = maxPages, db = db)
    with BlockHeaderSchema
    with TransactionSchema {

  import config.profile.api._

  val dao: BlockDao =
    BlockDao(config)(db.config.db.ioExecutionContext)

  /**
    * Generates a [[Pagination]] instance for each page to query.
    */
  def generateData(currentCacheSize: Int): Pagination =
    Pagination.unsafe(
      offset  = currentCacheSize,
      limit   = limitPerPage,
      reverse = reverse
    )

  private def generateBlockHeader(): BlockHeader =
    BlockHeader(
      hash         = new BlockEntry.Hash(BlockHash.generate),
      timestamp    = TimeStamp.now(),
      chainFrom    = GroupIndex.unsafe(1),
      chainTo      = GroupIndex.unsafe(16),
      height       = Height.genesis,
      mainChain    = Random.nextBoolean(),
      nonce        = ByteString.emptyByteString,
      version      = 0,
      depStateHash = Blake2b.generate,
      txsHash      = Blake2b.generate,
      txsCount     = scala.math.abs(Random.nextInt()),
      target       = ByteString.emptyByteString,
      hashrate     = BigInteger.ONE
    )

  private def generateTransactions(header: BlockHeader): Seq[TransactionEntity] =
    List.fill(transactionsPerBlock) {
      TransactionEntity(
        hash      = new Transaction.Hash(Hash.generate),
        blockHash = header.hash,
        timestamp = header.timestamp,
        chainFrom = GroupIndex.unsafe(1),
        chainTo   = GroupIndex.unsafe(16),
        gasAmount = 0,
        gasPrice  = U256.unsafe(0),
        index     = 0,
        mainChain = header.mainChain
      )
    }

  def persist(cache: Array[Pagination]): Unit = {
    logger.info(s"Generating data. Pages: ${cache.last.offset + 1}. Limit: ${cache.last.limit}.")

    val blocks       = List.fill(cache.length * limitPerPage)(generateBlockHeader()) //generate blocks
    val transactions = blocks flatMap generateTransactions //generate transactions for each block

    //drop existing tables
    val _ = db.dropTableIfExists(blockHeadersTable)
    val _ = db.dropTableIfExists(transactionsTable)

    logger.info(s"Persisting ${blockHeadersTable.baseTableRow.tableName} data")

    //Persist blocks
    val persistBlocks =
      blockHeadersTable.schema.create
        .andThen(createBlockHeadersIndexesSQL())
        .andThen(blockHeadersTable ++= blocks)

    val _ = db.runNow(
      action  = persistBlocks,
      timeout = batchWriteTimeout
    )

    logger.info(s"Persisting ${transactionsTable.baseTableRow.tableName} data")

    //Persist transactions
    val persistTransactions =
      transactionsTable.schema.create
        .andThen(createTransactionMainChainIndex())
        .andThen(transactionsTable ++= transactions)

    val _ = db.runNow(
      action  = persistTransactions,
      timeout = batchWriteTimeout
    )

    logger.info("Persisting data complete")
  }
}

object ListBlocksReadStateSettings {
  // if maxPage is 100 and limitPerPage is 20 then
  // the number of generated blocks will be 100 * 20

  val maxPages: Int             = 10000
  val limitPerPage: Int         = 20
  val transactionsPerBlock: Int = 5
}
// scalastyle:off magic.number

/**
  * JMH State For forward iteration with disabled connection pooling
  */
@State(Scope.Thread)
@SuppressWarnings(Array("org.wartremover.warts.Overloading"))
class ListBlocks_Forward_DisabledCP_ReadState(override val db: DBExecutor)
    extends ListBlocksReadState(reverse              = false,
                                maxPages             = maxPages,
                                limitPerPage         = limitPerPage,
                                transactionsPerBlock = transactionsPerBlock,
                                db                   = db) {

  def this() = {
    this(DBExecutor(dbName, dbHost, dbPort, DBConnectionPool.Disabled))
  }
}

/**
  * JMH State For forward iteration with disabled connection pooling
  */
@State(Scope.Thread)
@SuppressWarnings(Array("org.wartremover.warts.Overloading"))
class ListBlocks_Reverse_DisabledCP_ReadState(override val db: DBExecutor)
    extends ListBlocksReadState(reverse              = true,
                                maxPages             = maxPages,
                                limitPerPage         = limitPerPage,
                                transactionsPerBlock = transactionsPerBlock,
                                db                   = db) {
  def this() = {
    this(db = DBExecutor(dbName, dbHost, dbPort, DBConnectionPool.Disabled))
  }
}

/**
  * JMH State For forward iteration with HikariCP.
  *
  *  Reverse benchmark with HikariCP is not required because
  *  these benchmarks are actually for when connection pooling is
  *  disabled to prove that raw SQL queries are faster with minimal
  *  connections whereas typed queries require more connections to be faster.
  */
@State(Scope.Thread)
@SuppressWarnings(Array("org.wartremover.warts.Overloading"))
class ListBlocks_Forward_HikariCP_ReadState(override val db: DBExecutor)
    extends ListBlocksReadState(reverse              = false,
                                maxPages             = maxPages,
                                limitPerPage         = limitPerPage,
                                transactionsPerBlock = transactionsPerBlock,
                                db                   = db) {

  def this() = {
    this(db = DBExecutor(dbName, dbHost, dbPort, DBConnectionPool.HikariCP))
  }
}
// scalastyle:on magic.number
