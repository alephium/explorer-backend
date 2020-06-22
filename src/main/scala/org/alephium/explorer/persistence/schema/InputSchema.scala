package org.alephium.explorer.persistence.schema

import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import slick.lifted.{Index, ProvenShape}

import org.alephium.explorer.api.model.Transaction
import org.alephium.explorer.persistence.model.InputEntity

trait InputSchema extends CustomTypes {
  val config: DatabaseConfig[JdbcProfile]

  import config.profile.api._

  class Inputs(tag: Tag) extends Table[InputEntity](tag, "inputs") {
    def txHash: Rep[Transaction.Hash]    = column[Transaction.Hash]("tx_hash")
    def shortKey: Rep[Int]               = column[Int]("short_key")
    def txHashRef: Rep[Transaction.Hash] = column[Transaction.Hash]("tx_hash_ref")
    def outputIndex: Rep[Int]            = column[Int]("output_index")

    def inputsTxHashIdx: Index = index("inputs_tx_hash_idx", txHash)

    def * : ProvenShape[InputEntity] =
      (txHash, shortKey, txHashRef, outputIndex) <> ((InputEntity.apply _).tupled, InputEntity.unapply)
  }

  val inputsTable: TableQuery[Inputs] = TableQuery[Inputs]
}
