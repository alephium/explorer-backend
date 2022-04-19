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

case class TransactionRow(hashId: Array[Byte], blockhash: Array[Byte], blockIntId: Int)

object TableTransaction {

  val table: TableQuery[TableTransaction] = TableQuery[TableTransaction]

}

class TableTransaction(tag: Tag) extends Table[TransactionRow](tag, "table_transaction") {
  def hash: Rep[Array[Byte]]      = column[Array[Byte]]("hash_id", O.PrimaryKey, O.SqlType("BYTEA"))
  def blockHash: Rep[Array[Byte]] = column[Array[Byte]]("block_hash", O.SqlType("BYTEA"))
  def blockIntId: Rep[Int]        = column[Int]("block_int_id")

  def blockHashIndex  = index("block_hash_index", blockHash)
  def blockIntIdIndex = index("block_int_index", blockIntId)

  def blahBoolean: Rep[Boolean] = column[Boolean]("blah_boolean")
  def blahByte: Rep[Byte]       = column[Byte]("blah_byte")

  def * : ProvenShape[TransactionRow] =
    (hash, blockHash, blockIntId) <> (TransactionRow.tupled, TransactionRow.unapply)
}
