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
import slick.lifted.{Index, ProvenShape}

import org.alephium.explorer.persistence.model.MempoolTransactionEntity
import org.alephium.explorer.persistence.schema.CustomJdbcTypes._
import org.alephium.protocol.model.{GroupIndex, TransactionId}
import org.alephium.util.{TimeStamp, U256}

object MempoolTransactionSchema extends Schema[MempoolTransactionEntity]("utransactions") {

  class MempoolTransactions(tag: Tag) extends Table[MempoolTransactionEntity](tag, name) {
    def hash: Rep[TransactionId] =
      column[TransactionId]("hash", O.PrimaryKey, O.SqlType("BYTEA"))
    def chainFrom: Rep[GroupIndex] = column[GroupIndex]("chain_from")
    def chainTo: Rep[GroupIndex]   = column[GroupIndex]("chain_to")
    def gasAmount: Rep[Int]        = column[Int]("gas_amount")
    def gasPrice: Rep[U256] =
      column[U256]("gas_price", O.SqlType("DECIMAL(80,0)")) // U256.MaxValue has 78 digits
    def lastSeen: Rep[TimeStamp] = column[TimeStamp]("last_seen")

    def lastSeenIdx: Index = index("utransactions_last_seen_idx", lastSeen)

    def * : ProvenShape[MempoolTransactionEntity] =
      (hash, chainFrom, chainTo, gasAmount, gasPrice, lastSeen)
        .<>((MempoolTransactionEntity.apply _).tupled, MempoolTransactionEntity.unapply)
  }

  val table: TableQuery[MempoolTransactions] = TableQuery[MempoolTransactions]
}
