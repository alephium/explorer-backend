// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.schema

import slick.jdbc.PostgresProfile.api._
import slick.lifted.ProvenShape

import org.alephium.explorer.persistence.model.HolderEntity
import org.alephium.explorer.persistence.schema.CustomJdbcTypes._
import org.alephium.protocol.model.Address
import org.alephium.util.U256

object AlphHolderSchema extends SchemaMainChain[HolderEntity]("alph_holders") {

  class AlphHolders(tag: Tag) extends Table[HolderEntity](tag, name) {
    def address: Rep[Address] = column[Address]("address", O.PrimaryKey)
    def balance: Rep[U256] =
      column[U256]("balance", O.SqlType("DECIMAL(80,0)")) // U256.MaxValue has 78 digits

    def * : ProvenShape[HolderEntity] =
      (
        address,
        balance
      )
        .<>((HolderEntity.apply _).tupled, HolderEntity.unapply)
  }

  val table: TableQuery[AlphHolders] = TableQuery[AlphHolders]
}
