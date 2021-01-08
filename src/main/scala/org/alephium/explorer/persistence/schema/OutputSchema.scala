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
import org.alephium.explorer.api.model.{Address, BlockEntry, Transaction}
import org.alephium.explorer.persistence.model.OutputEntity
import org.alephium.util.TimeStamp

trait OutputSchema extends CustomTypes {
  val config: DatabaseConfig[JdbcProfile]

  import config.profile.api._

  class Outputs(tag: Tag) extends Table[OutputEntity](tag, "outputs") {
    def blockHash: Rep[BlockEntry.Hash] = column[BlockEntry.Hash]("block_hash")
    def txHash: Rep[Transaction.Hash]   = column[Transaction.Hash]("tx_hash")
    def amount: Rep[Double]             = column[Double]("amount")
    def address: Rep[Address]           = column[Address]("address")
    def key: Rep[Hash]                  = column[Hash]("key")
    def timestamp: Rep[TimeStamp]       = column[TimeStamp]("timestamp")
    def mainChain: Rep[Boolean]         = column[Boolean]("main_chain")

    def pk: PrimaryKey = primaryKey("outputs_pk", (key, blockHash))

    def keyIdx: Index       = index("outputs_key_idx", key)
    def blockHashIdx: Index = index("outputs_block_hash_idx", blockHash)
    def txHashIdx: Index    = index("outputs_tx_hash_idx", txHash)
    def addressIdx: Index   = index("outputs_address_idx", address)
    def timestampIdx: Index = index("outputs_timestamp_idx", timestamp)

    def * : ProvenShape[OutputEntity] =
      (blockHash, txHash, amount, address, key, timestamp, mainChain)
        .<>((OutputEntity.apply _).tupled, OutputEntity.unapply)
  }

  val outputsTable: TableQuery[Outputs] = TableQuery[Outputs]
}
