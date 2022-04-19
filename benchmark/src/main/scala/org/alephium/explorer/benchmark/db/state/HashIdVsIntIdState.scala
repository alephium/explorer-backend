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

import org.alephium.explorer.Hash
import org.alephium.explorer.benchmark.db.{DBConnectionPool, DBExecutor}
import org.alephium.explorer.benchmark.db.BenchmarkSettings._
import org.alephium.explorer.benchmark.db.table.{
  BlockRow,
  TableBlock,
  TableTransaction,
  TransactionRow
}
import org.openjdk.jmh.annotations.{Scope, State}

import scala.util.Random

case class BlockAndTransactions(block: BlockRow, transactions: Seq[TransactionRow])

@State(Scope.Thread)
@SuppressWarnings(Array("org.wartremover.warts.Overloading"))
class HashIdVsIntIdState(blocksCount: Int               = 10000,
                         transactionsPerBlockCount: Int = 100,
                         val db: DBExecutor)
    extends ReadBenchmarkState[BlockAndTransactions](testDataCount = blocksCount, db = db) {

  def this() =
    this(db = DBExecutor(dbName, dbHost, dbPort, DBConnectionPool.Disabled))

  import config.profile.api._

  def hashJoin(blockAndTransactions: BlockAndTransactions) =
    sql"""
       select block_int_id from table_transaction
            left join table_block on table_transaction.block_hash = table_block.hash_id
        where table_block.int_id = ${blockAndTransactions.block.intId};
       """.as[Int]

  def intJoin(blockAndTransactions: BlockAndTransactions) =
    sql"""
       select block_int_id from table_transaction
            left join table_block on table_transaction.block_int_id = table_block.int_id
        where table_block.int_id = ${blockAndTransactions.block.intId};
       """.as[Int]

  def generateData(currentCacheSize: Int): BlockAndTransactions = {
    val block =
      BlockRow(
        hashId    = Hash.generate.bytes.toArray,
        intId     = currentCacheSize,
        mainChain = currentCacheSize % 2 == 0,
        version   = Random.nextBytes(1).head
      )

    val transactions =
      List.fill(transactionsPerBlockCount) {
        TransactionRow(
          hashId     = Hash.generate.bytes.toArray,
          blockhash  = block.hashId,
          blockIntId = block.intId
        )
      }

    BlockAndTransactions(block, transactions)
  }

  def persist(data: Array[BlockAndTransactions]): Unit = {
    val createBlocksQuery =
      TableBlock.table.schema.dropIfExists
        .andThen(TableBlock.table.schema.create)
        .andThen(TableBlock.table ++= data.map(_.block))

    val createTransactionsQuery =
      TableTransaction.table.schema.dropIfExists
        .andThen(TableTransaction.table.schema.create)
        .andThen(TableTransaction.table ++= data.flatMap(_.transactions))

    val query =
      createBlocksQuery andThen createTransactionsQuery

    val _ = db.runNow(
      action  = query,
      timeout = batchWriteTimeout
    )
  }
}
