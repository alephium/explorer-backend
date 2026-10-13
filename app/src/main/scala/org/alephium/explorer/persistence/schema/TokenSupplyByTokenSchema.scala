// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.schema

import slick.jdbc.PostgresProfile.api._
import slick.lifted.{PrimaryKey, ProvenShape}

import org.alephium.explorer.persistence.model.TokenSupplyByTokenEntity
import org.alephium.explorer.persistence.schema.CustomJdbcTypes._
import org.alephium.protocol.model.TokenId
import org.alephium.util.{TimeStamp, U256}

object TokenSupplyByTokenSchema extends Schema[TokenSupplyByTokenEntity]("token_supply_by_token") {

  class TokenSuppliesByToken(tag: Tag) extends Table[TokenSupplyByTokenEntity](tag, name) {
    def token: Rep[TokenId] = column[TokenId]("token")
    def timestamp: Rep[TimeStamp] = column[TimeStamp]("block_timestamp")
    def total: Rep[U256] =
      column[U256]("total", O.SqlType("DECIMAL(80,0)")) // U256.MaxValue has 78 digits

    def pk: PrimaryKey = primaryKey("token_supply_by_token_pk", (token, timestamp))

    def * : ProvenShape[TokenSupplyByTokenEntity] =
      (token, timestamp, total)
        .<>((TokenSupplyByTokenEntity.apply _).tupled, TokenSupplyByTokenEntity.unapply)
  }

  val table: TableQuery[TokenSuppliesByToken] = TableQuery[TokenSuppliesByToken]
}
