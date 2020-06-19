package org.alephium.explorer.persistence.schema

import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import slick.lifted.{Index, ProvenShape}

import org.alephium.explorer.Hash
import org.alephium.explorer.persistence.model.OutputEntity

trait OutputSchema extends CustomTypes {
  val config: DatabaseConfig[JdbcProfile]

  import config.profile.api._

  class Outputs(tag: Tag) extends Table[OutputEntity](tag, "outputs") {
    def txHash: Rep[Hash]  = column[Hash]("tx_hash")
    def value: Rep[Long]   = column[Long]("value")
    def address: Rep[Hash] = column[Hash]("address")
    def shortKey: Rep[Int] = column[Int]("short_key")

    def outputsTxHashIdx: Index = index("outputs_tx_hash_idx", txHash)

    def * : ProvenShape[OutputEntity] =
      (txHash, value, address, shortKey) <> ((OutputEntity.apply _).tupled, OutputEntity.unapply)
  }

  val outputsTable: TableQuery[Outputs] = TableQuery[Outputs]
}
