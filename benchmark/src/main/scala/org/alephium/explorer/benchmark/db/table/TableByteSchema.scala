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

package org.alephium.explorer.benchmark.db.table

import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import slick.lifted.ProvenShape

trait TableByteSchema {

  val config: DatabaseConfig[JdbcProfile]

  import config.profile.api._

  /**
    * Table with single column that stores byte arrays
    */
  class TableBytea(tag: Tag) extends Table[Array[Byte]](tag, "table_bytea") {
    def hash: Rep[Array[Byte]] = column[Array[Byte]]("hash", O.PrimaryKey, O.SqlType("BYTEA"))

    def * : ProvenShape[Array[Byte]] = hash
  }

  val tableByteaQuery: TableQuery[TableBytea] = TableQuery[TableBytea]

}
