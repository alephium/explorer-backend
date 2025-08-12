// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.schema

import slick.jdbc.PostgresProfile.api._
import slick.lifted.ProvenShape

import org.alephium.explorer.api.model.NFTMetadata
import org.alephium.explorer.persistence.schema.CustomJdbcTypes._
import org.alephium.protocol.model.{ContractId, TokenId}
import org.alephium.util.U256

object NFTMetadataSchema extends SchemaMainChain[NFTMetadata]("nft_metadata") {

  class NFTMetadatas(tag: Tag) extends Table[NFTMetadata](tag, name) {
    def token: Rep[TokenId]           = column[TokenId]("token", O.PrimaryKey)
    def tokenUri: Rep[String]         = column[String]("token_uri")
    def collectionId: Rep[ContractId] = column[ContractId]("collection_id")
    def nftIndex: Rep[U256]           = column[U256]("nft_index", O.SqlType("DECIMAL(80,0)"))

    def * : ProvenShape[NFTMetadata] =
      (token, tokenUri, collectionId, nftIndex)
        .<>((NFTMetadata.apply _).tupled, NFTMetadata.unapply)
  }

  val table: TableQuery[NFTMetadatas] = TableQuery[NFTMetadatas]
}
