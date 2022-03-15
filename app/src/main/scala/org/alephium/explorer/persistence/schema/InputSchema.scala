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

import org.alephium.explorer.Hash
import org.alephium.explorer.api.model.{BlockEntry, Transaction}
import org.alephium.explorer.persistence.model.InputEntity
import org.alephium.util.TimeStamp

object InputSchema extends SchemaMainChain[InputEntity]("inputs") {

  class Inputs(tag: Tag) extends Table[InputEntity](tag, name) {
    def blockHash: Rep[BlockEntry.Hash]   = column[BlockEntry.Hash]("block_hash", O.SqlType("BYTEA"))
    def txHash: Rep[Transaction.Hash]     = column[Transaction.Hash]("tx_hash", O.SqlType("BYTEA"))
    def timestamp: Rep[TimeStamp]         = column[TimeStamp]("timestamp")
    def hint: Rep[Int]                    = column[Int]("hint")
    def outputRefKey: Rep[Hash]           = column[Hash]("output_ref_key", O.SqlType("BYTEA"))
    def unlockScript: Rep[Option[String]] = column[Option[String]]("unlock_script")
    def mainChain: Rep[Boolean]           = column[Boolean]("main_chain")
    def order: Rep[Int]                   = column[Int]("order")
    def txIndex: Rep[Int]                 = column[Int]("tx_index")

    def pk: PrimaryKey = primaryKey("inputs_pk", (outputRefKey, blockHash))

    def blockHashIdx: Index    = index("inputs_block_hash_idx", blockHash)
    def inputsTxHashIdx: Index = index("inputs_tx_hash_idx", txHash)
    def outputRefKeyIdx: Index = index("inputs_output_ref_key_idx", outputRefKey)
    def timestampIdx: Index    = index("inputs_timestamp_idx", timestamp)

    def * : ProvenShape[InputEntity] =
      (blockHash, txHash, timestamp, hint, outputRefKey, unlockScript, mainChain, order, txIndex)
        .<>((InputEntity.apply _).tupled, InputEntity.unapply)
  }

  val table: TableQuery[Inputs] = TableQuery[Inputs]
}
