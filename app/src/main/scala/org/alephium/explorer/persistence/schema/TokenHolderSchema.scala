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
import slick.lifted.{PrimaryKey, ProvenShape}

import org.alephium.explorer.persistence.model.TokenHolderEntity
import org.alephium.explorer.persistence.schema.CustomJdbcTypes._
import org.alephium.protocol.model.{Address, TokenId}
import org.alephium.util.U256

object TokenHolderSchema extends SchemaMainChain[TokenHolderEntity]("token_holders") {

  class TokenHolders(tag: Tag) extends Table[TokenHolderEntity](tag, name) {
    def address: Rep[Address] = column[Address]("address")
    def token: Rep[TokenId]   = column[TokenId]("token")
    def balance: Rep[U256] =
      column[U256]("balance", O.SqlType("DECIMAL(80,0)")) // U256.MaxValue has 78 digits

    def pk: PrimaryKey = primaryKey("token_holders_pk", (address, token))

    def * : ProvenShape[TokenHolderEntity] =
      (
        address,
        token,
        balance
      )
        .<>((TokenHolderEntity.apply _).tupled, TokenHolderEntity.unapply)
  }

  val table: TableQuery[TokenHolders] = TableQuery[TokenHolders]
}
