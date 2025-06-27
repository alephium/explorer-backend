// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

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
