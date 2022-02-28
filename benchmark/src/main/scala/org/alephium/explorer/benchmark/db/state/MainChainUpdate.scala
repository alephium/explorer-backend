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
import org.alephium.explorer.BlockHash
import org.alephium.explorer.api.model.{BlockEntry, GroupIndex, Height}
import org.alephium.explorer.benchmark.db.{DBConnectionPool, DBExecutor}
import org.alephium.explorer.benchmark.db.BenchmarkSettings._
import org.alephium.explorer.persistence.dao.BlockDao
import org.alephium.explorer.persistence.model.BlockHeader
import org.alephium.explorer.persistence.schema._
import org.alephium.util.TimeStamp

/**
  * JMH state for benchmarking reads to [[BlockHeaderSchema]] when
  * main_chain index exists vs dropped.
  */
class MainChainUpdate(testDataCount: Int, fork: Boolean, val db: DBExecutor)
    extends ReadBenchmarkState[BlockHeader](testDataCount, db = db)
    with BlockHeaderSchema
    with TransactionSchema
    with InputSchema
    with OutputSchema {

  import config.profile.api._

  val chainFrom: GroupIndex = GroupIndex.unsafe(0)
  val chainTo: GroupIndex   = GroupIndex.unsafe(0)
  val groupNum: Int         = 4

  val dao: BlockDao =
    BlockDao(groupNum, config)(db.config.db.ioExecutionContext)

  def generateData(currentCacheSize: Int): BlockHeader =
    BlockHeader(
      hash         = new BlockEntry.Hash(BlockHash.generate),
      timestamp    = TimeStamp.now(),
      chainFrom    = chainFrom,
      chainTo      = chainTo,
      height       = Height.genesis,
      mainChain    = false,
      nonce        = ByteString.emptyByteString,
      version      = 0,
      depStateHash = Blake2b.generate,
      txsHash      = Blake2b.generate,
      txsCount     = Random.nextInt(),
      target       = ByteString.emptyByteString,
      hashrate     = BigInteger.ONE,
      parent       = None
    )

  def persist(data: Array[BlockHeader]): Unit = {
    //create a fresh table and insert the data
    val chain = data.zipWithIndex.map {
      case (block, index) =>
        if (index > 0) {
          block.copy(parent = Some(data(index - 1).hash), height = Height.unsafe(index))
        } else {
          block
        }
    } ++ (if (fork) {
            data.zipWithIndex.map {
              case (block, index) =>
                block.copy(hash   = new BlockEntry.Hash(BlockHash.generate),
                           height = Height.unsafe(index))
            }
          } else {
            Array.empty
          })

    val _ = db.dropTableIfExists(blockHeadersTable)
    val _ = db.dropTableIfExists(transactionsTable)
    val _ = db.dropTableIfExists(inputsTable)
    val _ = db.dropTableIfExists(outputsTable)

    val query =
      blockHeadersTable.schema.create
        .andThen(transactionsTable.schema.create)
        .andThen(inputsTable.schema.create)
        .andThen(outputsTable.schema.create)
        .andThen(createBlockHeadersIndexesSQL())
        .andThen(blockHeadersTable ++= chain)

    val _ = db.runNow(
      action  = query,
      timeout = batchWriteTimeout
    )
  }
}

// scalastyle:off magic.number
@State(Scope.Thread)
@SuppressWarnings(Array("org.wartremover.warts.Overloading"))
class MainChainUpdateStateNoFork(override val db: DBExecutor)
    extends MainChainUpdate(testDataCount = 1000, false, db = db) {
  def this() = {
    this(DBExecutor(dbName, dbHost, dbPort, DBConnectionPool.HikariCP))
  }
}
@State(Scope.Thread)
@SuppressWarnings(Array("org.wartremover.warts.Overloading"))
class MainChainUpdateStateFork(override val db: DBExecutor)
    extends MainChainUpdate(testDataCount = 1000, true, db = db) {
  def this() = {
    this(DBExecutor(dbName, dbHost, dbPort, DBConnectionPool.HikariCP))
  }
}
