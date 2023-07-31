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

import akka.util.ByteString
import slick.jdbc.PostgresProfile.api._
import slick.lifted.{Index, PrimaryKey, ProvenShape}

import org.alephium.explorer.api.model.Token
import org.alephium.explorer.persistence.model.UOutputEntity
import org.alephium.explorer.persistence.schema.CustomJdbcTypes._
import org.alephium.protocol.Hash
import org.alephium.protocol.model.{Address, TransactionId}
import org.alephium.util.{TimeStamp, U256}

object UOutputSchema extends Schema[UOutputEntity]("uoutputs") {

  class UOutputs(tag: Tag) extends Table[UOutputEntity](tag, name) {
    def txHash: Rep[TransactionId] = column[TransactionId]("tx_hash", O.SqlType("BYTEA"))
    def hint: Rep[Int]             = column[Int]("hint")
    def key: Rep[Hash]             = column[Hash]("key", O.SqlType("BYTEA"))
    def amount: Rep[U256] =
      column[U256]("amount", O.SqlType("DECIMAL(80,0)")) // U256.MaxValue has 78 digits
    def address: Rep[Address]                = column[Address]("address")
    def tokens: Rep[Option[ArraySeq[Token]]] = column[Option[ArraySeq[Token]]]("tokens")
    def lockTime: Rep[Option[TimeStamp]]     = column[Option[TimeStamp]]("lock_time")
    def message: Rep[Option[ByteString]]     = column[Option[ByteString]]("message")
    def uoutputOrder: Rep[Int]               = column[Int]("uoutput_order")

    def pk: PrimaryKey = primaryKey("uoutputs_pk", (txHash, address, uoutputOrder))

    def txHashIdx: Index = index("uoutputs_tx_hash_idx", txHash)

    def * : ProvenShape[UOutputEntity] =
      (txHash, hint, key, amount, address, tokens, lockTime, message, uoutputOrder)
        .<>((UOutputEntity.apply _).tupled, UOutputEntity.unapply)
  }

  val table: TableQuery[UOutputs] = TableQuery[UOutputs]
}
