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

import java.math.BigInteger

import slick.jdbc.PostgresProfile.api._
import slick.lifted.{Index, ProvenShape}

import org.alephium.explorer.Hash
import org.alephium.explorer.api.model.Transaction
import org.alephium.explorer.persistence.schema.CustomJdbcTypes._

object TempSpentSchema extends SchemaMainChain[(BigInteger, Hash, Transaction.Hash)]("temp_spent") {

  class Spents(tag: Tag) extends Table[(BigInteger, Hash, Transaction.Hash)](tag, name) {
    def rowNumber: Rep[BigInteger]    = column[BigInteger]("row_number")
    def key: Rep[Hash]                = column[Hash]("key", O.SqlType("BYTEA"))
    def txHash: Rep[Transaction.Hash] = column[Transaction.Hash]("tx_hash", O.SqlType("BYTEA"))

    def keyIdx: Index       = index("temp_spent_key_idx", key)
    def rowNumberIdx: Index = index("temp_spent_row_number_idx", rowNumber)

    def * : ProvenShape[(BigInteger, Hash, Transaction.Hash)] = (rowNumber, key, txHash)

  }

  val table: TableQuery[Spents] = TableQuery[Spents]
}
