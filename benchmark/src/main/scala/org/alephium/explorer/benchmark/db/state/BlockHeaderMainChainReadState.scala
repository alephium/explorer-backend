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
import org.alephium.explorer.persistence.model.BlockHeader
import org.alephium.explorer.persistence.schema.BlockHeaderSchema
import org.alephium.util.TimeStamp

/**
  * JMH state for benchmarking reads to [[BlockHeaderSchema]] when
  * main_chain index exists vs dropped.
  */
class BlockHeaderMainChainReadState(dropMainChainIndex: Boolean,
                                    testDataCount: Int,
                                    val db: DBExecutor)
    extends ReadBenchmarkState[BlockHeader](testDataCount = testDataCount, db = db)
    with BlockHeaderSchema {

  import config.profile.api._

  def generateData(currentCacheSize: Int): BlockHeader =
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
      target       = ByteString.emptyByteString,
      hashrate     = BigInteger.ONE
    )

  def persist(data: Array[BlockHeader]): Unit = {
    //create a fresh table and insert the data
    val query =
      blockHeadersTable.schema.dropIfExists
        .andThen(blockHeadersTable.schema.create)
        .andThen(createBlockHeadersIndexesSQL())
        .andThen {
          //drop main_chain if dropMainChainIndex is true
          if (dropMainChainIndex) {
            sqlu"drop index blocks_main_chain_idx"
          } else {
            DBIO.successful(0)
          }
        }
        .andThen(blockHeadersTable ++= data)

    val _ = db.runNow(
      action  = query,
      timeout = batchWriteTimeout
    )
  }
}

/**
  * JMH state when main_chain index is dropped
  */
@State(Scope.Thread)
@SuppressWarnings(Array("org.wartremover.warts.Overloading"))
class BlockHeaderWithoutMainChainReadState(testDataCount: Int, override val db: DBExecutor)
    extends BlockHeaderMainChainReadState(dropMainChainIndex = true,
                                          testDataCount      = testDataCount,
                                          db                 = db) {
  def this() = {
    this(readDataCount, DBExecutor(dbName, dbHost, dbPort, DBConnectionPool.HikariCP))
  }
}

/**
  * JMH state when main_chain index exists
  */
@State(Scope.Thread)
@SuppressWarnings(Array("org.wartremover.warts.Overloading"))
class BlockHeaderWithMainChainReadState(testDataCount: Int, override val db: DBExecutor)
    extends BlockHeaderMainChainReadState(dropMainChainIndex = false,
                                          testDataCount      = testDataCount,
                                          db                 = db) {
  def this() = {
    this(readDataCount, DBExecutor(dbName, dbHost, dbPort, DBConnectionPool.HikariCP))
  }
}
