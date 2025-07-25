// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.schema

import akka.util.ByteString
import slick.jdbc.PostgresProfile.api._
import slick.lifted.{Index, PrimaryKey, ProvenShape}

import org.alephium.explorer.persistence.model.{GrouplessAddress, UInputEntity}
import org.alephium.explorer.persistence.schema.CustomJdbcTypes._
import org.alephium.protocol.Hash
import org.alephium.protocol.model.{Address, TransactionId}

object UInputSchema extends Schema[UInputEntity]("uinputs") {

  class UInputs(tag: Tag) extends Table[UInputEntity](tag, name) {
    def txHash: Rep[TransactionId]            = column[TransactionId]("tx_hash", O.SqlType("BYTEA"))
    def hint: Rep[Int]                        = column[Int]("hint")
    def outputRefKey: Rep[Hash]               = column[Hash]("output_ref_key", O.SqlType("BYTEA"))
    def unlockScript: Rep[Option[ByteString]] = column[Option[ByteString]]("unlock_script")
    def address: Rep[Option[Address]]         = column[Option[Address]]("address")
    def grouplessAddress: Rep[Option[GrouplessAddress]] =
      column[Option[GrouplessAddress]]("groupless_address")
    def uinputOrder: Rep[Int] = column[Int]("uinput_order")

    def pk: PrimaryKey = primaryKey("uinputs_pk", (outputRefKey, txHash))

    def uinputsTxHashIdx: Index       = index("uinputs_tx_hash_idx", txHash)
    def uinputsP2pkhAddressIdx: Index = index("uinputs_address_idx", address)

    def * : ProvenShape[UInputEntity] =
      (txHash, hint, outputRefKey, unlockScript, address, grouplessAddress, uinputOrder)
        .<>((UInputEntity.apply _).tupled, UInputEntity.unapply)
  }

  val table: TableQuery[UInputs] = TableQuery[UInputs]
}
