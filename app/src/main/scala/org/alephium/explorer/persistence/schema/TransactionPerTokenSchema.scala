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

import slick.jdbc.PostgresProfile.api._
import slick.lifted.{Index, PrimaryKey, ProvenShape}
import slick.sql.SqlAction

import org.alephium.explorer.persistence.model.TransactionPerTokenEntity
import org.alephium.explorer.persistence.schema.CustomJdbcTypes._
import org.alephium.protocol.model.{BlockHash, TokenId, TransactionId}
import org.alephium.util.TimeStamp

object TransactionPerTokenSchema
    extends SchemaMainChain[TransactionPerTokenEntity]("transaction_per_token") {

  class TransactionPerTokens(tag: Tag) extends Table[TransactionPerTokenEntity](tag, name) {
    def hash: Rep[TransactionId]  = column[TransactionId]("tx_hash", O.SqlType("BYTEA"))
    def blockHash: Rep[BlockHash] = column[BlockHash]("block_hash", O.SqlType("BYTEA"))
    def token: Rep[TokenId]       = column[TokenId]("token")
    def timestamp: Rep[TimeStamp] = column[TimeStamp]("block_timestamp")
    def txOrder: Rep[Int]         = column[Int]("tx_order")
    def mainChain: Rep[Boolean]   = column[Boolean]("main_chain")

    def pk: PrimaryKey = primaryKey("transaction_per_token_pk", (hash, blockHash, token))

    def hashIdx: Index      = index("transaction_per_token_hash_idx", hash)
    def blockHashIdx: Index = index("transaction_per_token_block_hash_idx", blockHash)
    def tokenIdx: Index     = index("transaction_per_token_token_idx", token)

    def * : ProvenShape[TransactionPerTokenEntity] =
      (hash, blockHash, token, timestamp, txOrder, mainChain)
        .<>((TransactionPerTokenEntity.apply _).tupled, TransactionPerTokenEntity.unapply)
  }

  /**
    * Need for multi-column index `block_timestamp_txn_order_idx`?
    *
    * Postgres uses this index for queries that order by `block_timestamp desc, tx_order asc`
    * and have large number of resulting rows.
    */
  def timestampTxnOrderIndex(): SqlAction[Int, NoStream, Effect] =
    sqlu"""
        create index if not exists #${name}_block_timestamp_txn_order_idx
                on #$name (block_timestamp desc, tx_order asc);
        """

  def timestampIndex(): SqlAction[Int, NoStream, Effect] =
    sqlu"""
        create index if not exists #${name}_timestamp_idx
                on #$name (block_timestamp desc);
        """

  def createSQLIndexes(): DBIO[Unit] =
    DBIO.seq(timestampTxnOrderIndex(), timestampIndex())

  val table: TableQuery[TransactionPerTokens] = TableQuery[TransactionPerTokens]
}
