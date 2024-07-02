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
import scala.util.Random

import akka.util.ByteString
import org.openjdk.jmh.annotations.{Scope, State}

import org.alephium.crypto.Blake2b
import org.alephium.explorer.api.model.Height
import org.alephium.explorer.benchmark.db.{DBConnectionPool, DBExecutor}
import org.alephium.explorer.benchmark.db.BenchmarkSettings._
import org.alephium.explorer.persistence.model.BlockHeader
import org.alephium.explorer.persistence.schema.BlockHeaderSchema
import org.alephium.protocol.model.{BlockHash, GroupIndex}
import org.alephium.util.TimeStamp

/** JMH state for benchmarking reads to
  * [[org.alephium.explorer.persistence.schema.BlockHeaderSchema]] when main_chain index exists vs
  * dropped.
  */
class BlockHeaderMainChainReadState(
    dropMainChainIndex: Boolean,
    testDataCount: Int,
    val db: DBExecutor
) extends ReadBenchmarkState[BlockHeader](testDataCount = testDataCount, db = db) {

  import config.profile.api._

  def generateData(currentCacheSize: Int): BlockHeader =
    BlockHeader(
      hash = BlockHash.generate,
      timestamp = TimeStamp.now(),
      chainFrom = new GroupIndex(1),
      chainTo = new GroupIndex(16),
      height = Height.genesis,
      mainChain = Random.nextBoolean(),
      nonce = ByteString.emptyByteString,
      version = 0,
      depStateHash = Blake2b.generate,
      txsHash = Blake2b.generate,
      txsCount = Random.nextInt(),
      target = ByteString.emptyByteString,
      hashrate = BigInteger.ONE,
      parent = Some(BlockHash.generate),
      deps = ArraySeq.from(0 to 4).map(_ => BlockHash.generate),
      ghostUncles = None
    )

  def persist(data: Array[BlockHeader]): Unit = {
    // create a fresh table and insert the data
    val query =
      BlockHeaderSchema.table.schema.dropIfExists
        .andThen(BlockHeaderSchema.table.schema.create)
        .andThen(BlockHeaderSchema.createBlockHeadersIndexes())
        .andThen {
          // drop main_chain if dropMainChainIndex is true
          if (dropMainChainIndex) {
            DBIO.seq(
              sqlu"drop index #${BlockHeaderSchema.name + "_main_chain_idx"}",
              sqlu"drop index #${BlockHeaderSchema.name + "_full_index"}"
            )
          } else {
            DBIO.seq(
              sqlu"drop index #${BlockHeaderSchema.name + "_full_index"}"
            )
          }
        }
        .andThen(BlockHeaderSchema.table ++= data)

    val _ = db.runNow(
      action = query,
      timeout = batchWriteTimeout
    )
  }
}

/** JMH state when main_chain index is dropped
  */
@State(Scope.Thread)
@SuppressWarnings(Array("org.wartremover.warts.Overloading"))
class BlockHeaderWithoutMainChainReadState(testDataCount: Int, override val db: DBExecutor)
    extends BlockHeaderMainChainReadState(
      dropMainChainIndex = true,
      testDataCount = testDataCount,
      db = db
    ) {
  def this() = {
    this(readDataCount, DBExecutor(dbName, dbHost, dbPort, DBConnectionPool.Disabled))
  }
}

/** JMH state when main_chain index exists
  */
@State(Scope.Thread)
@SuppressWarnings(Array("org.wartremover.warts.Overloading"))
class BlockHeaderWithMainChainReadState(testDataCount: Int, override val db: DBExecutor)
    extends BlockHeaderMainChainReadState(
      dropMainChainIndex = false,
      testDataCount = testDataCount,
      db = db
    ) {
  def this() = {
    this(readDataCount, DBExecutor(dbName, dbHost, dbPort, DBConnectionPool.Disabled))
  }
}
