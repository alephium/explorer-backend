// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.schema

import org.apache.pekko.util.ByteString
import slick.jdbc.PostgresProfile.api._
import slick.lifted.ProvenShape

import org.alephium.explorer.persistence.model.NFTMetadataEntity
import org.alephium.explorer.persistence.schema.CustomJdbcTypes._
import org.alephium.protocol.model.{ContractId, TokenId}
import org.alephium.util.U256

object NFTMetadataSchema extends SchemaMainChain[NFTMetadataEntity]("nft_metadata") {

  class NFTMetadatas(tag: Tag) extends Table[NFTMetadataEntity](tag, name) {
    def token: Rep[TokenId]           = column[TokenId]("token", O.PrimaryKey)
    def tokenUri: Rep[ByteString]     = column[ByteString]("token_uri")
    def collectionId: Rep[ContractId] = column[ContractId]("collection_id")
    def nftIndex: Rep[U256]           = column[U256]("nft_index", O.SqlType("DECIMAL(80,0)"))

    def * : ProvenShape[NFTMetadataEntity] =
      (token, tokenUri, collectionId, nftIndex)
        .<>((NFTMetadataEntity.apply _).tupled, NFTMetadataEntity.unapply)
  }

  val table: TableQuery[NFTMetadatas] = TableQuery[NFTMetadatas]
}
