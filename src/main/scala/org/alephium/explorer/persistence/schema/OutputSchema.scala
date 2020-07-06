package org.alephium.explorer.persistence.schema

import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import slick.lifted.{Index, ProvenShape}

import org.alephium.explorer.Hash
import org.alephium.explorer.api.model.{Address, Transaction}
import org.alephium.explorer.persistence.model.OutputEntity
import org.alephium.util.TimeStamp

trait OutputSchema extends CustomTypes {
  val config: DatabaseConfig[JdbcProfile]

  import config.profile.api._

  class Outputs(tag: Tag) extends Table[OutputEntity](tag, "outputs") {
    def txHash: Rep[Transaction.Hash] = column[Transaction.Hash]("tx_hash")
    def amount: Rep[Long]             = column[Long]("amount")
    def createdHeight: Rep[Int]       = column[Int]("created_height")
    def address: Rep[Address]         = column[Address]("address")
    def outputRefKey: Rep[Hash]       = column[Hash]("output_ref")
    def timestamp: Rep[TimeStamp]     = column[TimeStamp]("timestamp")

    def outputsTxHashIdx: Index = index("outputs_tx_hash_idx", txHash)

    def * : ProvenShape[OutputEntity] =
      (txHash, amount, createdHeight, address, outputRefKey, timestamp) <> ((OutputEntity.apply _).tupled, OutputEntity.unapply)
  }

  val outputsTable: TableQuery[Outputs] = TableQuery[Outputs]
}
