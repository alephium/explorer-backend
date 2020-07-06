package org.alephium.explorer.persistence.schema

import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import slick.lifted.{Index, ProvenShape}

import org.alephium.explorer.Hash
import org.alephium.explorer.api.model.Transaction
import org.alephium.explorer.persistence.model.InputEntity

trait InputSchema extends CustomTypes {
  val config: DatabaseConfig[JdbcProfile]

  import config.profile.api._

  class Inputs(tag: Tag) extends Table[InputEntity](tag, "inputs") {
    def txHash: Rep[Transaction.Hash] = column[Transaction.Hash]("tx_hash")
    def scriptHint: Rep[Int]          = column[Int]("script_hint")
    def key: Rep[Hash]                = column[Hash]("key")
    def unlockScript: Rep[String]     = column[String]("unlock_script")

    def inputsTxHashIdx: Index = index("inputs_tx_hash_idx", txHash)

    def * : ProvenShape[InputEntity] =
      (txHash, scriptHint, key, unlockScript) <> ((InputEntity.apply _).tupled, InputEntity.unapply)
  }

  val inputsTable: TableQuery[Inputs] = TableQuery[Inputs]
}
