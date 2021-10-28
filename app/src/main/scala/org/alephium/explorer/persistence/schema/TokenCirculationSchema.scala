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
import slick.lifted.ProvenShape

import org.alephium.explorer.persistence.model.TokenCirculationEntity
import org.alephium.util.{TimeStamp, U256}

trait TokenCirculationSchema extends CustomTypes {
  val config: DatabaseConfig[JdbcProfile]

  import config.profile.api._

  class TokenCirculations(tag: Tag)
      extends Table[TokenCirculationEntity](tag, "token_circulation") {
    def timestamp: Rep[TimeStamp] = column[TimeStamp]("timestamp")
    def amount: Rep[U256] =
      column[U256]("amount", O.SqlType("DECIMAL(80,0)")) //U256.MaxValue has 78 digits
    def * : ProvenShape[TokenCirculationEntity] =
      (timestamp, amount)
        .<>((TokenCirculationEntity.apply _).tupled, TokenCirculationEntity.unapply)
  }

  val tokenCirculationTable: TableQuery[TokenCirculations] = TableQuery[TokenCirculations]
}
