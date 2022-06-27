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

import akka.util.ByteString
import slick.jdbc.PostgresProfile.api._
import slick.lifted.{Index, PrimaryKey, ProvenShape}

import org.alephium.explorer.Hash
import org.alephium.explorer.api.model.{Address, BlockEntry, Transaction}
import org.alephium.explorer.persistence.DBActionW
import org.alephium.explorer.persistence.model.TokenOutputEntity
import org.alephium.explorer.persistence.schema.CustomJdbcTypes._
import org.alephium.util.{TimeStamp, U256}

object TokenOutputSchema extends SchemaMainChain[TokenOutputEntity]("token_outputs") {

  class TokenOutputs(tag: Tag) extends Table[TokenOutputEntity](tag, name) {
    def blockHash: Rep[BlockEntry.Hash] = column[BlockEntry.Hash]("block_hash", O.SqlType("BYTEA"))
    def txHash: Rep[Transaction.Hash]   = column[Transaction.Hash]("tx_hash", O.SqlType("BYTEA"))
    def timestamp: Rep[TimeStamp]       = column[TimeStamp]("block_timestamp")
    def outputType: Rep[Int]            = column[Int]("output_type")
    def hint: Rep[Int]                  = column[Int]("hint")
    def key: Rep[Hash]                  = column[Hash]("key", O.SqlType("BYTEA"))
    def token: Rep[Hash]                = column[Hash]("token")
    def amount: Rep[U256] =
      column[U256]("amount", O.SqlType("DECIMAL(80,0)")) //U256.MaxValue has 78 digits
    def address: Rep[Address]            = column[Address]("address")
    def mainChain: Rep[Boolean]          = column[Boolean]("main_chain")
    def lockTime: Rep[Option[TimeStamp]] = column[Option[TimeStamp]]("lock_time")
    def message: Rep[Option[ByteString]] = column[Option[ByteString]]("message")
    def outputOrder: Rep[Int]            = column[Int]("output_order")
    def txOrder: Rep[Int]                = column[Int]("tx_order")
    def spentFinalized: Rep[Option[Transaction.Hash]] =
      column[Option[Transaction.Hash]]("spent_finalized", O.Default(None))

    def pk: PrimaryKey = primaryKey("token_outputs_pk", (key, token, blockHash))

    def keyIdx: Index       = index("token_outputs_key_idx", key)
    def blockHashIdx: Index = index("token_outputs_block_hash_idx", blockHash)
    def txHashIdx: Index    = index("token_outputs_tx_hash_idx", txHash)
    def addressIdx: Index   = index("token_outputs_address_idx", address)
    def timestampIdx: Index = index("token_outputs_timestamp_idx", timestamp)

    def * : ProvenShape[TokenOutputEntity] =
      (blockHash,
       txHash,
       timestamp,
       outputType,
       hint,
       key,
       token,
       amount,
       address,
       mainChain,
       lockTime,
       message,
       outputOrder,
       txOrder,
       spentFinalized)
        .<>((TokenOutputEntity.apply _).tupled, TokenOutputEntity.unapply)
  }

  lazy val createNonSpentIndex: DBActionW[Int] =
    sqlu"create unique index if not exists non_spent_output_idx on #${name} (address, main_chain, key, block_hash) where spent_finalized IS NULL;"

  val table: TableQuery[TokenOutputs] = TableQuery[TokenOutputs]
}
