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

import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import slick.lifted.{Index, PrimaryKey, ProvenShape}

import org.alephium.explorer.api.model.{BlockEntry, Transaction}
import org.alephium.explorer.persistence.model.TransactionEntity
import org.alephium.util.{TimeStamp, U256}

trait TransactionSchema extends CustomTypes {
  val config: DatabaseConfig[JdbcProfile]

  import config.profile.api._

  class Transactions(tag: Tag) extends Table[TransactionEntity](tag, "transactions") {
    def hash: Rep[Transaction.Hash]     = column[Transaction.Hash]("hash")
    def blockHash: Rep[BlockEntry.Hash] = column[BlockEntry.Hash]("block_hash")
    def timestamp: Rep[TimeStamp]       = column[TimeStamp]("timestamp")
    def startGas: Rep[Int]              = column[Int]("start-gas")
    def gasPrice: Rep[U256] =
      column[U256]("gas-price", O.SqlType("DECIMAL(80,0)")) //U256.MaxValue has 78 digits
    def txIndex: Rep[Int] = column[Int]("index")

    def pk: PrimaryKey = primaryKey("txs_pk", (hash, blockHash))

    def hashIdx: Index      = index("txs_hash_idx", hash)
    def timestampIdx: Index = index("txs_timestamp_idx", timestamp)
    def blockHashIdx: Index = index("txs_block_hash_idx", blockHash)

    def * : ProvenShape[TransactionEntity] =
      (hash, blockHash, timestamp, startGas, gasPrice, txIndex)
        .<>((TransactionEntity.apply _).tupled, TransactionEntity.unapply)
  }

  val transactionsTable: TableQuery[Transactions] = TableQuery[Transactions]
}