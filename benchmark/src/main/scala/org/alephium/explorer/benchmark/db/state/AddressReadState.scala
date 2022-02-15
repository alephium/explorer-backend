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

import scala.concurrent.{Await, ExecutionContext}
import scala.util.Random

import akka.util.ByteString
import org.openjdk.jmh.annotations.{Scope, State}
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import org.alephium.crypto.Blake2b
import org.alephium.explorer.{BlockHash, Hash}
import org.alephium.explorer.api.model._
import org.alephium.explorer.benchmark.db.{DBConnectionPool, DBExecutor}
import org.alephium.explorer.benchmark.db.BenchmarkSettings._
import org.alephium.explorer.persistence.dao.{BlockDao, TransactionDao}
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.persistence.queries.TransactionQueries
import org.alephium.explorer.persistence.schema._
import org.alephium.protocol.ALPH
import org.alephium.util.{Base58, TimeStamp, U256}

class Queries(val config: DatabaseConfig[JdbcProfile])(
    implicit val executionContext: ExecutionContext)
    extends TransactionQueries

/**
  * JMH state for benchmarking reads from TransactionDao
  */
@SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
class AddressReadState(val db: DBExecutor)
    extends ReadBenchmarkState[OutputEntity](testDataCount = 40000, db = db)
    with TransactionQueries
    with BlockHeaderSchema
    with InputSchema
    with OutputSchema
    with TransactionPerAddressSchema {

  val ec: ExecutionContext = ExecutionContext.global

  implicit val executionContext: ExecutionContext = ec
  import config.profile.api._

  val blockDao: BlockDao =
    BlockDao(4, config)(db.config.db.ioExecutionContext)

  val dao: TransactionDao =
    TransactionDao(config)(db.config.db.ioExecutionContext)

  val queries: TransactionQueries = new Queries(db.config)

  val address: Address = Address.unsafe(Base58.encode(Hash.generate.bytes))

  var blocks: Seq[BlockEntity] = _

  val pagination: Pagination = Pagination.unsafe(
    offset  = 0,
    limit   = 100,
    reverse = false
  )

  private def generateInput(blockHash: BlockEntry.Hash,
                            txHash: Transaction.Hash,
                            timestamp: TimeStamp,
                            hint: Int,
                            key: Hash): InputEntity = {
    InputEntity(
      blockHash    = blockHash,
      txHash       = txHash,
      timestamp    = timestamp,
      hint         = hint,
      outputRefKey = key,
      unlockScript = None,
      mainChain    = true,
      order        = 0,
      None,
      None,
      None
    )
  }
  private def generateTransaction(blockHash: BlockEntry.Hash,
                                  txHash: Transaction.Hash,
                                  timestamp: TimeStamp): TransactionEntity =
    TransactionEntity(
      hash      = txHash,
      blockHash = blockHash,
      timestamp = timestamp,
      chainFrom = GroupIndex.unsafe(1),
      chainTo   = GroupIndex.unsafe(3),
      gasAmount = 0,
      gasPrice  = U256.unsafe(0),
      index     = 0,
      mainChain = true
    )

  def generateData(currentCacheSize: Int): OutputEntity = {
    val blockHash = new BlockEntry.Hash(BlockHash.generate)
    val txHash    = new Transaction.Hash(Hash.generate)
    val timestamp = TimeStamp.now()

    OutputEntity(
      blockHash = blockHash,
      txHash    = txHash,
      timestamp = timestamp,
      hint      = Random.nextInt(),
      key       = Hash.generate,
      amount    = ALPH.alph(1),
      address   = address,
      mainChain = true,
      lockTime  = None,
      order     = 0,
      None
    )
  }

  def persist(cache: Array[OutputEntity]): Unit = {
    logger.info(s"Generating transactions data.")

    var outputs = cache
    blocks = cache.map { output =>
      val blockHash = output.blockHash
      val txHash    = output.txHash
      val timestamp = output.timestamp
      val inputs = if (Random.nextBoolean()) {
        val output = outputs(Random.nextInt(outputs.length))
        outputs = outputs.filter(_ != output)
        Seq(generateInput(blockHash, txHash, timestamp, output.hint, output.key))
      } else {
        Seq.empty
      }

      BlockEntity(
        hash         = blockHash,
        timestamp    = timestamp,
        chainFrom    = GroupIndex.unsafe(1),
        chainTo      = GroupIndex.unsafe(16),
        height       = Height.genesis,
        deps         = Seq.empty,
        transactions = Seq(generateTransaction(blockHash, txHash, timestamp)),
        inputs       = inputs,
        outputs      = Seq(output),
        mainChain    = true,
        nonce        = ByteString.emptyByteString,
        version      = 0,
        depStateHash = Blake2b.generate,
        txsHash      = Blake2b.generate,
        target       = ByteString.emptyByteString,
        hashrate     = BigInteger.ONE
      )
    }

    //drop existing tables
    val _ = db.dropTableIfExists(blockHeadersTable)
    val _ = db.dropTableIfExists(transactionsTable)
    val _ = db.dropTableIfExists(inputsTable)
    val _ = db.dropTableIfExists(outputsTable)
    val _ = db.dropTableIfExists(transactionPerAddressesTable)

    val createTable =
      blockHeadersTable.schema.create
        .andThen(transactionsTable.schema.create)
        .andThen(inputsTable.schema.create)
        .andThen(outputsTable.schema.create)
        .andThen(transactionPerAddressesTable.schema.create)
        .andThen(createBlockHeadersIndexesSQL())
        .andThen(createTransactionMainChainIndex())
        .andThen(createInputMainChainIndex())
        .andThen(createOutputMainChainIndex())
        .andThen(createTransactionPerAddressMainChainIndex())

    val _ = db.runNow(
      action  = createTable,
      timeout = batchWriteTimeout
    )

    logger.info("Persisting data")
    blocks.sliding(10000).foreach { bs =>
      val txs = bs.flatMap(_.transactions)
      Await.result(blockDao.insertAll(bs.toSeq), batchWriteTimeout)
      val _ =
        db.runNow(insertTxPerAddressFromOutputs(bs.toSeq.flatMap(_.outputs.map(out => (out, 0)))),
                  batchWriteTimeout)

      blocks
        .flatMap(_.inputs)
        .sliding(10000)
        .foreach(_.foreach { input =>
          Await.result(blockDao.updateSpent(input), batchWriteTimeout)
          Await.result(blockDao.updateAddress(input), batchWriteTimeout)
          db.runNow(updateTxPerAddressFromInputs(input, 0), batchWriteTimeout)
        })
    }

    logger.info("Persisting data complete")
  }
}

// scalastyle:off magic.number

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
class Address_ReadState(override val db: DBExecutor) extends AddressReadState(db = db) {

  def this() = {
    this(db = DBExecutor(dbName, dbHost, dbPort, DBConnectionPool.HikariCP))
  }
}
// scalastyle:on magic.number
