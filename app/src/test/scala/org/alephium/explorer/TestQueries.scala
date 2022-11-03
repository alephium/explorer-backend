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

package org.alephium.explorer

import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.persistence._
import org.alephium.explorer.persistence.model.TransactionPerAddressEntity
import org.alephium.explorer.persistence.schema.TransactionPerAddressSchema

object TestQueries {

  def insert(entities: Seq[TransactionPerAddressEntity]): DBActionW[Option[Int]] =
    TransactionPerAddressSchema.table ++= entities

  def clearTransactionPerAddressTable(): DBActionW[Int] =
    TransactionPerAddressSchema.table.delete

  def clearAndInsert(entities: Seq[TransactionPerAddressEntity]): DBActionW[Option[Int]] =
    clearTransactionPerAddressTable() andThen insert(entities)
}
