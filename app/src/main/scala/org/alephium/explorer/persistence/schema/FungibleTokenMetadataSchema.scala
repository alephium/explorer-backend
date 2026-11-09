// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.schema

import org.apache.pekko.util.ByteString
import slick.jdbc.PostgresProfile.api._
import slick.lifted.ProvenShape

import org.alephium.explorer.persistence.model.FungibleTokenMetadataEntity
import org.alephium.explorer.persistence.schema.CustomJdbcTypes._
import org.alephium.protocol.model.TokenId
import org.alephium.util.U256

object FungibleTokenMetadataSchema
    extends SchemaMainChain[FungibleTokenMetadataEntity]("fungible_token_metadata") {

  class TokenInfos(tag: Tag) extends Table[FungibleTokenMetadataEntity](tag, name) {
    def token: Rep[TokenId]     = column[TokenId]("token", O.PrimaryKey)
    def symbol: Rep[ByteString] = column[ByteString]("symbol")
    def name: Rep[ByteString]   = column[ByteString]("name")
    def decimals: Rep[U256]     = column[U256]("decimals", O.SqlType("DECIMAL(80,0)"))

    def * : ProvenShape[FungibleTokenMetadataEntity] =
      (token, symbol, name, decimals)
        .<>((FungibleTokenMetadataEntity.apply _).tupled, FungibleTokenMetadataEntity.unapply)
  }

  val table: TableQuery[TokenInfos] = TableQuery[TokenInfos]
}
