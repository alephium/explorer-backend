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
import slick.lifted.{Index, ProvenShape}

import org.alephium.explorer.api.model.{BlockEntry, Transaction}
import org.alephium.explorer.persistence.model.TransactionEntity
import org.alephium.util.TimeStamp

trait TransactionSchema extends CustomTypes {
  val config: DatabaseConfig[JdbcProfile]

  import config.profile.api._

  class Transactions(tag: Tag) extends Table[TransactionEntity](tag, "transactions") {
    def hash: Rep[Transaction.Hash]     = column[Transaction.Hash]("hash", O.PrimaryKey)
    def blockHash: Rep[BlockEntry.Hash] = column[BlockEntry.Hash]("block_hash")
    def timestamp: Rep[TimeStamp]       = column[TimeStamp]("timestamp")

    def transactionsBlockHashIdx: Index = index("transactions_block_hash_idx", blockHash)

    def * : ProvenShape[TransactionEntity] =
      (hash, blockHash, timestamp) <> ((TransactionEntity.apply _).tupled, TransactionEntity.unapply)
  }

  val transactionsTable: TableQuery[Transactions] = TableQuery[Transactions]
}
