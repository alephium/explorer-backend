// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.schema

import akka.util.ByteString
import slick.jdbc.PostgresProfile.api._
import slick.lifted.{Index, PrimaryKey, ProvenShape}

import org.alephium.explorer.persistence.model.{ContractEntity, InterfaceIdEntity}
import org.alephium.explorer.persistence.schema.CustomJdbcTypes._
import org.alephium.protocol.model.{Address, BlockHash, TransactionId}
import org.alephium.util.TimeStamp

object ContractSchema extends SchemaMainChain[ContractEntity]("contracts") {

  class CreateSubContractEvents(tag: Tag) extends Table[ContractEntity](tag, name) {
    def contract: Rep[Address]       = column[Address]("contract")
    def parent: Rep[Option[Address]] = column[Option[Address]]("parent")
    def stdInterfaceIdGuessed: Rep[Option[ByteString]] =
      column[Option[ByteString]]("std_interface_id_guessed")
    def creationBlockHash: Rep[BlockHash] =
      column[BlockHash]("creation_block_hash", O.SqlType("BYTEA"))
    def creationTxHash: Rep[TransactionId] =
      column[TransactionId]("creation_tx_hash", O.SqlType("BYTEA"))
    def creationTimestamp: Rep[TimeStamp] = column[TimeStamp]("creation_timestamp")
    def creationEventOrder: Rep[Int]      = column[Int]("creation_event_order")
    def destructionBlockHash: Rep[Option[BlockHash]] =
      column[Option[BlockHash]]("destruction_block_hash", O.SqlType("BYTEA"))
    def destructionTxHash: Rep[Option[TransactionId]] =
      column[Option[TransactionId]]("destruction_tx_hash", O.SqlType("BYTEA"))
    def destructionTimestamp: Rep[Option[TimeStamp]] =
      column[Option[TimeStamp]]("destruction_timestamp")
    def destructionEventOrder: Rep[Option[Int]] = column[Option[Int]]("destruction_event_order")
    def category: Rep[Option[String]]           = column[Option[String]]("category")
    def interfaceId: Rep[Option[InterfaceIdEntity]] =
      column[Option[InterfaceIdEntity]]("interface_id")

    def * : ProvenShape[ContractEntity] =
      (
        contract,
        parent,
        stdInterfaceIdGuessed,
        creationBlockHash,
        creationTxHash,
        creationTimestamp,
        creationEventOrder,
        destructionBlockHash,
        destructionTxHash,
        destructionTimestamp,
        destructionEventOrder,
        category,
        interfaceId
      )
        .<>((ContractEntity.apply _).tupled, ContractEntity.unapply)

    def pk: PrimaryKey = primaryKey("contracts_pk", (contract, creationBlockHash))

    def contractIdx: Index = index("contracts_contract_idx", contract)
    def parentIdx: Index   = index("contracts_parent_idx", parent)
    def stdInterfaceIdGuessedIdx: Index =
      index("contracts_std_interface_id_guessed_idx", stdInterfaceIdGuessed)
    def categoryIdx: Index    = index("contracts_category_idx", category)
    def interfaceIdIdx: Index = index("contracts_interface_id_idx", interfaceId)
  }

  val table: TableQuery[CreateSubContractEvents] = TableQuery[CreateSubContractEvents]
}
