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

import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import slick.lifted.{Index, ProvenShape}

import org.alephium.explorer.api.model.{GroupIndex, Transaction}
import org.alephium.explorer.persistence.model.UTransactionEntity
import org.alephium.util.U256

trait UTransactionSchema extends CustomTypes {
  val config: DatabaseConfig[JdbcProfile]

  import config.profile.api._

  class UTransactions(tag: Tag) extends Table[UTransactionEntity](tag, "utransactions") {
    def hash: Rep[Transaction.Hash] = column[Transaction.Hash]("hash", O.PrimaryKey)
    def chainFrom: Rep[GroupIndex]  = column[GroupIndex]("chain_from")
    def chainTo: Rep[GroupIndex]    = column[GroupIndex]("chain_to")
    def startGas: Rep[Int]          = column[Int]("start-gas")
    def gasPrice: Rep[U256] =
      column[U256]("gas-price", O.SqlType("DECIMAL(80,0)")) //U256.MaxValue has 78 digits

    def hashIdx: Index = index("utxs_hash_idx", hash)

    def * : ProvenShape[UTransactionEntity] =
      (hash, chainFrom, chainTo, startGas, gasPrice)
        .<>((UTransactionEntity.apply _).tupled, UTransactionEntity.unapply)
  }

  val utransactionsTable: TableQuery[UTransactions] = TableQuery[UTransactions]
}
