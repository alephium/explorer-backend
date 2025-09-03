// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.schema

import slick.jdbc.PostgresProfile.api._
import slick.lifted.{Index, PrimaryKey, ProvenShape}

import org.alephium.explorer.persistence.model.TransactionPerTokenEntity
import org.alephium.explorer.persistence.schema.CustomJdbcTypes._
import org.alephium.protocol.model.{BlockHash, TokenId, TransactionId}
import org.alephium.util.TimeStamp

object TransactionPerTokenSchema
    extends SchemaMainChain[TransactionPerTokenEntity]("transaction_per_token") {

  class TransactionPerTokens(tag: Tag) extends Table[TransactionPerTokenEntity](tag, name) {
    def hash: Rep[TransactionId]         = column[TransactionId]("tx_hash", O.SqlType("BYTEA"))
    def blockHash: Rep[BlockHash]        = column[BlockHash]("block_hash", O.SqlType("BYTEA"))
    def token: Rep[TokenId]              = column[TokenId]("token")
    def timestamp: Rep[TimeStamp]        = column[TimeStamp]("block_timestamp")
    def txOrder: Rep[Int]                = column[Int]("tx_order")
    def mainChain: Rep[Boolean]          = column[Boolean]("main_chain")
    def conflicted: Rep[Option[Boolean]] = column[Option[Boolean]]("conflicted")

    def pk: PrimaryKey = primaryKey("transaction_per_token_pk", (hash, blockHash, token))

    def hashIdx: Index      = index("transaction_per_token_hash_idx", hash)
    def blockHashIdx: Index = index("transaction_per_token_block_hash_idx", blockHash)
    def tokenIdx: Index     = index("transaction_per_token_token_idx", token)

    def * : ProvenShape[TransactionPerTokenEntity] =
      (hash, blockHash, token, timestamp, txOrder, mainChain, conflicted)
        .<>((TransactionPerTokenEntity.apply _).tupled, TransactionPerTokenEntity.unapply)
  }

  def createIndexes(): DBIO[Unit] =
    DBIO.seq(
      CommonIndex.blockTimestampTxOrderIndex(this),
      CommonIndex.timestampIndex(this)
    )

  val table: TableQuery[TransactionPerTokens] = TableQuery[TransactionPerTokens]
}
