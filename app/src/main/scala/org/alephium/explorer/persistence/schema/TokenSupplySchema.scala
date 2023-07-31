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
import slick.lifted.ProvenShape

import org.alephium.explorer.persistence.model.TokenSupplyEntity
import org.alephium.explorer.persistence.schema.CustomJdbcTypes._
import org.alephium.util.{TimeStamp, U256}

object TokenSupplySchema extends Schema[TokenSupplyEntity]("token_supply") {

  class TokenSupplies(tag: Tag) extends Table[TokenSupplyEntity](tag, name) {
    def timestamp: Rep[TimeStamp] = column[TimeStamp]("block_timestamp", O.PrimaryKey)
    def total: Rep[U256] =
      column[U256]("total", O.SqlType("DECIMAL(80,0)")) // U256.MaxValue has 78 digits
    def circulating: Rep[U256] =
      column[U256]("circulating", O.SqlType("DECIMAL(80,0)")) // U256.MaxValue has 78 digits
    def reserved: Rep[U256] =
      column[U256]("reserved", O.SqlType("DECIMAL(80,0)")) // U256.MaxValue has 78 digits
    def locked: Rep[U256] =
      column[U256]("locked", O.SqlType("DECIMAL(80,0)")) // U256.MaxValue has 78 digits

    def * : ProvenShape[TokenSupplyEntity] =
      (timestamp, total, circulating, reserved, locked)
        .<>((TokenSupplyEntity.apply _).tupled, TokenSupplyEntity.unapply)
  }

  val table: TableQuery[TokenSupplies] = TableQuery[TokenSupplies]
}
