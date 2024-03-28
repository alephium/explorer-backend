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

import scala.collection.immutable.ArraySeq

import slick.jdbc.PostgresProfile.api._
import slick.lifted.{Index, PrimaryKey, ProvenShape}

import org.alephium.api.model.Val
import org.alephium.explorer.persistence.model.EventEntity
import org.alephium.explorer.persistence.schema.CustomJdbcTypes._
import org.alephium.protocol.model.{Address, BlockHash, TransactionId}
import org.alephium.util.TimeStamp

object EventSchema extends SchemaMainChain[EventEntity]("events") {

  class Events(tag: Tag) extends Table[EventEntity](tag, name) {
    def blockHash: Rep[BlockHash]          = column[BlockHash]("block_hash", O.SqlType("BYTEA"))
    def txHash: Rep[TransactionId]         = column[TransactionId]("tx_hash", O.SqlType("BYTEA"))
    def contractAddress: Rep[Address]      = column[Address]("contract_address")
    def inputAddress: Rep[Option[Address]] = column[Option[Address]]("input_address")
    def timestamp: Rep[TimeStamp]          = column[TimeStamp]("block_timestamp")
    def eventIndex: Rep[Int]               = column[Int]("event_index")
    def fields: Rep[ArraySeq[Val]]         = column[ArraySeq[Val]]("fields")
    def eventOrder: Rep[Int]               = column[Int]("event_order_in_block")
    def mainChain: Rep[Boolean]            = column[Boolean]("main_chain")

    def * : ProvenShape[EventEntity] =
      (
        blockHash,
        txHash,
        contractAddress,
        inputAddress,
        timestamp,
        eventIndex,
        fields,
        eventOrder,
        mainChain
      )
        .<>((EventEntity.apply _).tupled, EventEntity.unapply)

    def pk: PrimaryKey = primaryKey("events_pk", (blockHash, eventOrder))

    def txHashIdx: Index          = index("tx_hash_idx", txHash)
    def contractAddressIdx: Index = index("contract_address_idx", contractAddress)
    def inputAddressIdx: Index    = index("input_address_idx", inputAddress)
    def timestampIdx: Index       = index("block_timestamp_idx", timestamp)
  }

  val table: TableQuery[Events] = TableQuery[Events]
}
