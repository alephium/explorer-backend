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

import org.alephium.explorer.Hash
import org.alephium.explorer.api.model.{BlockEntry, Transaction}
import org.alephium.explorer.persistence.model.InputEntity

trait InputSchema extends CustomTypes {
  val config: DatabaseConfig[JdbcProfile]

  import config.profile.api._

  class Inputs(tag: Tag) extends Table[InputEntity](tag, "inputs") {
    def blockHash: Rep[BlockEntry.Hash] = column[BlockEntry.Hash]("block_hash")
    def txHash: Rep[Transaction.Hash]   = column[Transaction.Hash]("tx_hash")
    def scriptHint: Rep[Int]            = column[Int]("script_hint")
    def outputRefKey: Rep[Hash]         = column[Hash]("output_ref_key")
    def unlockScript: Rep[String]       = column[String]("unlock_script")
    def mainChain: Rep[Boolean]         = column[Boolean]("main_chain")

    def pk: PrimaryKey = primaryKey("inputs_pk", (outputRefKey, txHash, blockHash))

    def blockHashIdx: Index    = index("inputs_block_hash_idx", blockHash)
    def inputsTxHashIdx: Index = index("inputs_tx_hash_idx", txHash)
    def outputRefKeyIdx: Index = index("inputs_output_ref_key_idx", outputRefKey)

    def * : ProvenShape[InputEntity] =
      (blockHash, txHash, scriptHint, outputRefKey, unlockScript, mainChain)
        .<>((InputEntity.apply _).tupled, InputEntity.unapply)
  }

  val inputsTable: TableQuery[Inputs] = TableQuery[Inputs]
}
