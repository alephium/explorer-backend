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
import org.alephium.explorer.persistence.model.UInputEntity
import org.alephium.explorer.persistence.schema.CustomJdbcTypes._
import org.alephium.protocol.model.{Address, TransactionId}

object UInputSchema extends Schema[UInputEntity]("uinputs") {

  class UInputs(tag: Tag) extends Table[UInputEntity](tag, name) {
    def txHash: Rep[TransactionId]            = column[TransactionId]("tx_hash", O.SqlType("BYTEA"))
    def hint: Rep[Int]                        = column[Int]("hint")
    def outputRefKey: Rep[Hash]               = column[Hash]("output_ref_key", O.SqlType("BYTEA"))
    def unlockScript: Rep[Option[ByteString]] = column[Option[ByteString]]("unlock_script")
    def address: Rep[Option[Address]]         = column[Option[Address]]("address")
    def uinputOrder: Rep[Int]                 = column[Int]("uinput_order")

    def pk: PrimaryKey = primaryKey("uinputs_pk", (outputRefKey, txHash))

    def uinputsTxHashIdx: Index       = index("uinputs_tx_hash_idx", txHash)
    def uinputsP2pkhAddressIdx: Index = index("uinputs_address_idx", address)

    def * : ProvenShape[UInputEntity] =
      (txHash, hint, outputRefKey, unlockScript, address, uinputOrder)
        .<>((UInputEntity.apply _).tupled, UInputEntity.unapply)
  }

  val table: TableQuery[UInputs] = TableQuery[UInputs]
}
