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

import org.openjdk.jmh.annotations.{Scope, State}
import org.scalacheck.Gen

import org.alephium.explorer.GenApiModel._
import org.alephium.explorer.GenDBModel._
import org.alephium.explorer.benchmark.db.{DBConnectionPool, DBExecutor}
import org.alephium.explorer.benchmark.db.BenchmarkSettings._
import org.alephium.explorer.persistence.schema.TransactionPerTokenSchema
import org.alephium.protocol.Hash
import org.alephium.protocol.model.TokenId

sealed trait IndexType
object IndexType {
  case object TimeStampAndTxnOrder extends IndexType
  case object TimeStampAsc         extends IndexType
  case object TimeStampDesc        extends IndexType
}

class TransactionsPerTokenReadState(val indexType: IndexType,
                                    val db: DBExecutor,
                                    val tokensCount: Int          = 10,
                                    val transactionsPerToken: Int = 100000,
) extends ReadBenchmarkState[Hash](tokensCount, db) {

  import config.profile.api._

  override def generateData(currentCacheSize: Int): Hash =
    tokenIdGen.sample.get.value

  private def createIndex(): Int =
    indexType match {
      case IndexType.TimeStampAndTxnOrder =>
        db.runNow(TransactionPerTokenSchema.timestampTxnOrderIndex(), batchWriteTimeout)

      case IndexType.TimeStampDesc =>
        db.runNow(TransactionPerTokenSchema.timestampIndex(), batchWriteTimeout)

      case IndexType.TimeStampAsc =>
        val query =
          sqlu"""
              create index if not exists #${TransactionPerTokenSchema.name}_timestamp_idx
                      on #${TransactionPerTokenSchema.name} (block_timestamp desc);
              """

        db.runNow(query, batchWriteTimeout)
    }

  override def persist(tokens: Array[Hash]): Unit = {
    val _ = db.dropTableIfExists(TransactionPerTokenSchema.table)
    val _ = db.runNow(TransactionPerTokenSchema.table.schema.create, batchWriteTimeout)

    createIndex()

    tokens.zipWithIndex foreach {
      case (hash, tokenIndex) =>
        val generator =
          transactionPerTokenEntityGen(tokenId      = TokenId.unsafe(hash),
                                       mainChainGen = Gen.const(true))

        val generateFew =
          Gen
            .listOfN(transactionsPerToken, generator)
            .sample
            .get

        logger.info(s"Persisting token: ${tokenIndex + 1}/${tokens.length}")
        val action = TransactionPerTokenSchema.table ++= generateFew
        db.runNow(action, batchWriteTimeout)
    }

    logger.info("Persisting data complete")
  }
}

@State(Scope.Thread)
@SuppressWarnings(Array("org.wartremover.warts.Overloading"))
class TransactionsPerTokenReadState_MultiColumnIndex(override val db: DBExecutor)
    extends TransactionsPerTokenReadState(indexType = IndexType.TimeStampAndTxnOrder, db = db) {

  def this() = {
    this(db = DBExecutor(dbName, dbHost, dbPort, DBConnectionPool.Disabled))
  }
}

@State(Scope.Thread)
@SuppressWarnings(Array("org.wartremover.warts.Overloading"))
class TransactionsPerTokenReadState_TimestampIndex_ASC_Order(override val db: DBExecutor)
    extends TransactionsPerTokenReadState(indexType = IndexType.TimeStampAsc, db = db) {

  def this() = {
    this(db = DBExecutor(dbName, dbHost, dbPort, DBConnectionPool.Disabled))
  }
}

@State(Scope.Thread)
@SuppressWarnings(Array("org.wartremover.warts.Overloading"))
class TransactionsPerTokenReadState_TimestampIndex_DESC_Order(override val db: DBExecutor)
    extends TransactionsPerTokenReadState(indexType = IndexType.TimeStampDesc, db = db) {

  def this() = {
    this(db = DBExecutor(dbName, dbHost, dbPort, DBConnectionPool.Disabled))
  }
}
