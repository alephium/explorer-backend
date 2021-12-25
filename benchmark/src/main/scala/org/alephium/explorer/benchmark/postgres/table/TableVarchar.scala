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

package org.alephium.explorer.benchmark.postgres.table

import slick.jdbc.PostgresProfile.api._
import slick.lifted.Index
import slick.lifted.ProvenShape

object TableVarchar {
  val tableQuery: TableQuery[TableVarchar] = TableQuery[TableVarchar]
}

/**
  * Table with single column that stores String values
  */
class TableVarchar(tag: Tag) extends Table[String](tag, "varchar_hash_table") {
  def hash: Rep[String] = column[String]("hash", O.PrimaryKey)
  def hashIdx: Index    = index("varchar_hash_idx", hash)

  def * : ProvenShape[String] = hash
}
