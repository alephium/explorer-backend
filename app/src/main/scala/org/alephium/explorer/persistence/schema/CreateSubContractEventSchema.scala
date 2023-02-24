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

import org.alephium.explorer.persistence.model.CreateSubContractEventEntity
import org.alephium.explorer.persistence.schema.CustomJdbcTypes._
import org.alephium.protocol.model.{Address, BlockHash, TransactionId}
import org.alephium.util.TimeStamp

object CreateSubContractEventSchema
    extends SchemaMainChain[CreateSubContractEventEntity]("create_sub_contract_events") {

  class CreateSubContractEvents(tag: Tag) extends Table[CreateSubContractEventEntity](tag, name) {
    def blockHash: Rep[BlockHash]  = column[BlockHash]("block_hash", O.SqlType("BYTEA"))
    def txHash: Rep[TransactionId] = column[TransactionId]("tx_hash", O.SqlType("BYTEA"))
    def contract: Rep[Address]     = column[Address]("contract")
    def subContract: Rep[Address]  = column[Address]("sub_contract")
    def timestamp: Rep[TimeStamp]  = column[TimeStamp]("block_timestamp")
    def eventOrder: Rep[Int]       = column[Int]("event_order_in_block")

    def * : ProvenShape[CreateSubContractEventEntity] =
      (blockHash, txHash, contract, subContract, timestamp, eventOrder)
        .<>((CreateSubContractEventEntity.apply _).tupled, CreateSubContractEventEntity.unapply)

    def pk: PrimaryKey = primaryKey("create_sub_contract_events_pk", (blockHash, eventOrder))

    def contractIdx: Index    = index("create_sub_contract_events_contract_idx", contract)
    def subContractIdx: Index = index("create_sub_contract_events_sub_contract_idx", subContract)
  }

  val table: TableQuery[CreateSubContractEvents] = TableQuery[CreateSubContractEvents]
}
