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
import slick.lifted.{PrimaryKey, ProvenShape}
import slick.sql.SqlAction

import org.alephium.explorer.persistence.model.HashrateEntity
import org.alephium.util.TimeStamp

trait HashrateSchema extends CustomTypes {
  val config: DatabaseConfig[JdbcProfile]

  import config.profile.api._

  class Hashrates(tag: Tag) extends Table[HashrateEntity](tag, "hashrates") {
    def timestamp: Rep[TimeStamp] = column[TimeStamp]("timestamp")
    def value: Rep[BigDecimal]    = column[BigDecimal]("value")
    def intervalType: Rep[Int]    = column[Int]("interval_type")

    def pk: PrimaryKey = primaryKey("hashrates_pk", (timestamp, intervalType))

    def * : ProvenShape[HashrateEntity] =
      (timestamp, value, intervalType)
        .<>((HashrateEntity.apply _).tupled, HashrateEntity.unapply)
  }

  def createHashrateIntervalTypeIndex(): SqlAction[Int, NoStream, Effect] =
    sqlu"create index if not exists interval_type_idx on hashrates (interval_type)"

  val hashrateTable: TableQuery[Hashrates] = TableQuery[Hashrates]
}
