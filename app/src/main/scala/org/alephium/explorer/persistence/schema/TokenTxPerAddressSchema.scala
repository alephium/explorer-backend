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

import org.alephium.explorer.persistence.model.TokenTxPerAddressEntity
import org.alephium.explorer.persistence.schema.CustomJdbcTypes._
import org.alephium.protocol.model.{Address, BlockHash, TokenId, TransactionId}
import org.alephium.util.TimeStamp

object TokenPerAddressSchema
    extends SchemaMainChain[TokenTxPerAddressEntity]("token_tx_per_addresses") {

  class TokenPerAddresses(tag: Tag) extends Table[TokenTxPerAddressEntity](tag, name) {
    def address: Rep[Address]      = column[Address]("address")
    def txHash: Rep[TransactionId] = column[TransactionId]("tx_hash", O.SqlType("BYTEA"))
    def blockHash: Rep[BlockHash]  = column[BlockHash]("block_hash", O.SqlType("BYTEA"))
    def timestamp: Rep[TimeStamp]  = column[TimeStamp]("block_timestamp")
    def txOrder: Rep[Int]          = column[Int]("tx_order")
    def mainChain: Rep[Boolean]    = column[Boolean]("main_chain")
    def token: Rep[TokenId]        = column[TokenId]("token")

    def pk: PrimaryKey = primaryKey("token_tx_per_address_pk", (txHash, blockHash, address, token))

    def hashIdx: Index         = index("token_tx_per_address_hash_idx", txHash)
    def blockHashIdx: Index    = index("token_tx_per_address_block_hash_idx", blockHash)
    def addressIdx: Index      = index("token_tx_per_address_address_idx", address)
    def tokenIdx: Index        = index("token_tx_per_address_token_idx", token)
    def tokenAddressIdx: Index = index("token_tx_per_address_token_address_idx", (token, address))

    def * : ProvenShape[TokenTxPerAddressEntity] =
      (address, txHash, blockHash, timestamp, txOrder, mainChain, token)
        .<>((TokenTxPerAddressEntity.apply _).tupled, TokenTxPerAddressEntity.unapply)
  }

  def createSQLIndexes(): DBIO[Unit] =
    DBIO.seq(
      CommonIndex.blockTimestampTxOrderIndex(this),
      CommonIndex.timestampIndex(this)
    )

  val table: TableQuery[TokenPerAddresses] = TableQuery[TokenPerAddresses]
}
