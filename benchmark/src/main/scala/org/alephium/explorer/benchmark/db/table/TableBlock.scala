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

import slick.jdbc.PostgresProfile.api._
import slick.lifted.ProvenShape

case class BlockRow(hashId: Array[Byte], intId: Int, mainChain: Boolean, version: Byte)

object TableBlock {

  val table: TableQuery[TableBlock] = TableQuery[TableBlock]

}

class TableBlock(tag: Tag) extends Table[BlockRow](tag, "table_block") {
  def hashId: Rep[Array[Byte]] = column[Array[Byte]]("hash_id", O.PrimaryKey, O.SqlType("BYTEA"))
  def intId: Rep[Int]          = column[Int]("int_id", O.Unique)
  def mainChain: Rep[Boolean]  = column[Boolean]("main_chain")
  def version: Rep[Byte]       = column[Byte]("block_version")

  def * : ProvenShape[BlockRow] =
    (hashId, intId, mainChain, version) <> (BlockRow.tupled, BlockRow.unapply)
}
