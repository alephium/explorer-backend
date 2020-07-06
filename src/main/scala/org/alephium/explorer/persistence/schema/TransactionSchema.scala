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
    def hash: Rep[Transaction.Hash]     = column[Transaction.Hash]("hash")
    def blockHash: Rep[BlockEntry.Hash] = column[BlockEntry.Hash]("block_hash")
    def timestamp: Rep[TimeStamp]       = column[TimeStamp]("timestamp")

    def transactionsBlockHashIdx: Index = index("transactions_block_hash_idx", blockHash)

    def * : ProvenShape[TransactionEntity] =
      (hash, blockHash, timestamp) <> ((TransactionEntity.apply _).tupled, TransactionEntity.unapply)
  }

  val transactionsTable: TableQuery[Transactions] = TableQuery[Transactions]
}
