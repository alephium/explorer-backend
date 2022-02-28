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

package org.alephium.explorer.persistence.schema

import slick.lifted.{Index, PrimaryKey, ProvenShape}
import slick.sql.SqlAction

import org.alephium.explorer.api.model.{BlockEntry, GroupIndex,Height, Transaction}
import org.alephium.explorer.persistence.model.TransactionEntity
import org.alephium.util.{TimeStamp, U256}

trait TransactionSchema extends Schema with CustomTypes {
  import config.profile.api._

  private val tableName = "transactions"

  class Transactions(tag: Tag) extends Table[TransactionEntity](tag, tableName) {
    def hash: Rep[Transaction.Hash]     = column[Transaction.Hash]("hash", O.SqlType("BYTEA"))
    def blockHash: Rep[BlockEntry.Hash] = column[BlockEntry.Hash]("block_hash", O.SqlType("BYTEA"))
    def timestamp: Rep[TimeStamp]       = column[TimeStamp]("timestamp")
    def chainFrom: Rep[GroupIndex]      = column[GroupIndex]("chain_from")
    def chainTo: Rep[GroupIndex]        = column[GroupIndex]("chain_to")
    def gasAmount: Rep[Int]             = column[Int]("gas-amount")
    def gasPrice: Rep[U256] =
      column[U256]("gas-price", O.SqlType("DECIMAL(80,0)")) //U256.MaxValue has 78 digits
    def txIndex: Rep[Int]       = column[Int]("index")
    def mainChain: Rep[Boolean] = column[Boolean]("main_chain")
    def height: Rep[Height]        = column[Height]("height")

    def pk: PrimaryKey = primaryKey("txs_pk", (hash, blockHash))

    def hashIdx: Index      = index("txs_hash_idx", hash)
    def timestampIdx: Index = index("txs_timestamp_idx", timestamp)
    def blockHashIdx: Index = index("txs_block_hash_idx", blockHash)
    def chainFromIdx: Index = index("txs_chain_from_idx", chainFrom)
    def chainToIdx: Index   = index("txs_chain_to_idx", chainTo)

    def * : ProvenShape[TransactionEntity] =
      (hash, blockHash, timestamp, chainFrom, chainTo, gasAmount, gasPrice, txIndex, mainChain, height)
        .<>((TransactionEntity.apply _).tupled, TransactionEntity.unapply)
  }

  def createTransactionMainChainIndex(): SqlAction[Int, NoStream, Effect] =
    mainChainIndex(tableName)

  val transactionsTable: TableQuery[Transactions] = TableQuery[Transactions]
}
