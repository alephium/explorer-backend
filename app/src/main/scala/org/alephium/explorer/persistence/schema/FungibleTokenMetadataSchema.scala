// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.schema

import slick.jdbc.PostgresProfile.api._
import slick.lifted.ProvenShape

import org.alephium.explorer.api.model.FungibleTokenMetadata
import org.alephium.explorer.persistence.schema.CustomJdbcTypes._
import org.alephium.protocol.model.TokenId
import org.alephium.util.U256

object FungibleTokenMetadataSchema
    extends SchemaMainChain[FungibleTokenMetadata]("fungible_token_metadata") {

  class TokenInfos(tag: Tag) extends Table[FungibleTokenMetadata](tag, name) {
    def token: Rep[TokenId] = column[TokenId]("token", O.PrimaryKey)
    def symbol: Rep[String] = column[String]("symbol")
    def name: Rep[String]   = column[String]("name")
    def decimals: Rep[U256] = column[U256]("decimals", O.SqlType("DECIMAL(80,0)"))

    def * : ProvenShape[FungibleTokenMetadata] =
      (token, symbol, name, decimals)
        .<>((FungibleTokenMetadata.apply _).tupled, FungibleTokenMetadata.unapply)
  }

  val table: TableQuery[TokenInfos] = TableQuery[TokenInfos]
}
