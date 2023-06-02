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

import scala.collection.immutable.ArraySeq
import scala.concurrent.{Await, ExecutionContext}
import scala.util.Random

import akka.util.ByteString
import org.openjdk.jmh.annotations.{Scope, State}
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import org.alephium.crypto.Blake2b
import org.alephium.explorer.GroupSetting
import org.alephium.explorer.api.model._
import org.alephium.explorer.benchmark.db.{DataGenerator, DBConnectionPool, DBExecutor}
import org.alephium.explorer.benchmark.db.BenchmarkSettings._
import org.alephium.explorer.persistence.dao.BlockDao
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.persistence.queries.InputUpdateQueries
import org.alephium.explorer.persistence.schema._
import org.alephium.explorer.service.FinalizerService
import org.alephium.protocol.{ALPH, Hash}
import org.alephium.protocol.model.{Address, BlockHash, GroupIndex, TransactionId}
import org.alephium.util.{TimeStamp, U256}

class Queries(val config: DatabaseConfig[PostgresProfile])(implicit
    val executionContext: ExecutionContext
)

/** JMH state for benchmarking reads from TransactionDao
  */
// scalastyle:off magic.number
// scalastyle:off method.length
@SuppressWarnings(
  Array("org.wartremover.warts.OptionPartial", "org.wartremover.warts.GlobalExecutionContext")
)
class AddressReadState(val db: DBExecutor)
    extends ReadBenchmarkState[OutputEntity](testDataCount = 4000, db = db) {

  val ec: ExecutionContext = ExecutionContext.global

  implicit val executionContext: ExecutionContext              = ec
  implicit val databaseConfig: DatabaseConfig[PostgresProfile] = db.config

  import config.profile.api._

  val address: Address = DataGenerator.genAddress()

  var blocks: ArraySeq[BlockEntity] = _

  lazy val hashes: ArraySeq[(TransactionId, BlockHash)] = ArraySeq.from(
    Random
      .shuffle(blocks.flatMap(_.transactions.map(tx => (tx.hash, tx.blockHash))).toMap)
      .take(100)
  )

  lazy val txHashes: ArraySeq[TransactionId] = hashes.map(_._1)

  val pagination: Pagination = Pagination.unsafe(
    page = 1,
    limit = 100
  )

  private def generateInput(
      blockHash: BlockHash,
      txHash: TransactionId,
      timestamp: TimeStamp,
      hint: Int,
      key: Hash
  ): InputEntity = {
    InputEntity(
      blockHash = blockHash,
      txHash = txHash,
      timestamp = timestamp,
      hint = hint,
      outputRefKey = key,
      unlockScript = None,
      mainChain = true,
      inputOrder = 0,
      txOrder = 0,
      None,
      None,
      None,
      None
    )
  }
  private def generateTransaction(
      blockHash: BlockHash,
      txHash: TransactionId,
      timestamp: TimeStamp
  ): TransactionEntity =
    TransactionEntity(
      hash = txHash,
      blockHash = blockHash,
      timestamp = timestamp,
      chainFrom = new GroupIndex(1),
      chainTo = new GroupIndex(3),
      gasAmount = 0,
      gasPrice = U256.unsafe(0),
      order = 0,
      mainChain = true,
      scriptExecutionOk = Random.nextBoolean(),
      inputSignatures = None,
      scriptSignatures = None,
      coinbase = false
    )

  def generateData(currentCacheSize: Int): OutputEntity = {
    val blockHash = BlockHash.generate
    val txHash    = TransactionId.generate
    val timestamp = TimeStamp.now()

    OutputEntity(
      blockHash = blockHash,
      txHash = txHash,
      timestamp = timestamp,
      outputType = OutputEntity.OutputType.unsafe(Random.nextInt(2)),
      hint = Random.nextInt(),
      key = Hash.generate,
      amount = ALPH.alph(1),
      address = address,
      tokens = None,
      mainChain = true,
      lockTime = None,
      message = None,
      outputOrder = 0,
      txOrder = 0,
      coinbase = false,
      spentFinalized = None,
      spentTimestamp = None
    )
  }

  def persist(cache: Array[OutputEntity]): Unit = {
    logger.info(s"Generating transactions data.")

    var outputs = cache

    // 80% of outputs will be spent
    var spent = testDataCount * 0.8

    blocks = ArraySeq.from(cache.map { output =>
      val blockHash = output.blockHash
      val txHash    = output.txHash
      val timestamp = output.timestamp
      val inputs = if (spent >= 1) {
        spent = spent - 1
        val output = outputs(Random.nextInt(outputs.length))
        outputs = outputs.filter(_ != output)
        ArraySeq(generateInput(blockHash, txHash, timestamp, output.hint, output.key))
      } else {
        ArraySeq.empty
      }
      val transactions = ArraySeq(generateTransaction(blockHash, txHash, timestamp))

      BlockEntity(
        hash = blockHash,
        timestamp = timestamp,
        chainFrom = new GroupIndex(1),
        chainTo = new GroupIndex(16),
        height = Height.genesis,
        deps = ArraySeq.empty,
        transactions = transactions,
        inputs = inputs,
        outputs = ArraySeq(output),
        mainChain = true,
        nonce = ByteString.emptyByteString,
        version = 0,
        depStateHash = Blake2b.generate,
        txsHash = Blake2b.generate,
        target = ByteString.emptyByteString,
        hashrate = BigInteger.ONE
      )
    })

    // drop existing tables
    val _ = db.dropTableIfExists(BlockHeaderSchema.table)
    val _ = db.dropTableIfExists(TransactionSchema.table)
    val _ = db.dropTableIfExists(InputSchema.table)
    val _ = db.dropTableIfExists(OutputSchema.table)
    val _ = db.dropTableIfExists(TransactionPerAddressSchema.table)
    val _ = db.dropTableIfExists(AppStateSchema.table)

    val createTable =
      BlockHeaderSchema.table.schema.create
        .andThen(TransactionSchema.table.schema.create)
        .andThen(InputSchema.table.schema.create)
        .andThen(OutputSchema.table.schema.create)
        .andThen(TransactionPerAddressSchema.table.schema.create)
        .andThen(AppStateSchema.table.schema.create)
        .andThen(BlockHeaderSchema.createBlockHeadersIndexes())
        .andThen(TransactionSchema.createMainChainIndex())
        .andThen(InputSchema.createMainChainIndex())
        .andThen(OutputSchema.createMainChainIndex())
        .andThen(TransactionPerAddressSchema.createMainChainIndex())
        .andThen(OutputSchema.createNonSpentIndex())

    val _ = db.runNow(
      action = createTable,
      timeout = batchWriteTimeout
    )

    implicit val groupSetting: GroupSetting =
      GroupSetting(4)

    logger.info("Persisting data")
    blocks.sliding(10000).foreach { bs =>
      Await.result(BlockDao.insertAll(bs), batchWriteTimeout)
    }

    val from = ALPH.LaunchTimestamp
    val to   = DataGenerator.timestampMaxValue
    val _ =
      Await.result(
        FinalizerService.finalizeOutputsWith(from, to, to.deltaUnsafe(from)),
        batchWriteTimeout
      )

    val _ = db.runNow(
      action = InputUpdateQueries.updateInputs(),
      timeout = batchWriteTimeout
    )

    logger.info("Persisting data complete")
  }
}

// scalastyle:off magic.number

/** JMH State For forward iteration with HikariCP.
  *
  * Reverse benchmark with HikariCP is not required because these benchmarks are actually for when
  * connection pooling is disabled to prove that raw SQL queries are faster with minimal connections
  * whereas typed queries require more connections to be faster.
  */
@State(Scope.Thread)
@SuppressWarnings(Array("org.wartremover.warts.Overloading"))
class Address_ReadState(override val db: DBExecutor) extends AddressReadState(db = db) {

  def this() = {
    this(db = DBExecutor(dbName, dbHost, dbPort, DBConnectionPool.HikariCP))
  }
}
// scalastyle:on magic.number
