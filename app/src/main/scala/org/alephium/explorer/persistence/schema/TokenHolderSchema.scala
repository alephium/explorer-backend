// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.schema

import slick.jdbc.PostgresProfile.api._
import slick.lifted.{PrimaryKey, ProvenShape}

import org.alephium.explorer.persistence.model.TokenHolderEntity
import org.alephium.explorer.persistence.schema.CustomJdbcTypes._
import org.alephium.protocol.model.{Address, TokenId}
import org.alephium.util.U256

object TokenHolderSchema extends SchemaMainChain[TokenHolderEntity]("token_holders") {

  class TokenHolders(tag: Tag) extends Table[TokenHolderEntity](tag, name) {
    def address: Rep[Address] = column[Address]("address")
    def token: Rep[TokenId]   = column[TokenId]("token")
    def balance: Rep[U256] =
      column[U256]("balance", O.SqlType("DECIMAL(80,0)")) // U256.MaxValue has 78 digits

    def pk: PrimaryKey = primaryKey("token_holders_pk", (address, token))

    def * : ProvenShape[TokenHolderEntity] =
      (
        address,
        token,
        balance
      )
        .<>((TokenHolderEntity.apply _).tupled, TokenHolderEntity.unapply)
  }

  val table: TableQuery[TokenHolders] = TableQuery[TokenHolders]
}
