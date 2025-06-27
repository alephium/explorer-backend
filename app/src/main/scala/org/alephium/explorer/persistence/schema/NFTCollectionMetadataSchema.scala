// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.schema

import slick.jdbc.PostgresProfile.api._
import slick.lifted.ProvenShape

import org.alephium.explorer.api.model.NFTCollectionMetadata
import org.alephium.explorer.persistence.schema.CustomJdbcTypes._
import org.alephium.protocol.model.Address

object NFTCollectionMetadataSchema
    extends SchemaMainChain[NFTCollectionMetadata]("nft_collection_metadata") {

  class TokenInfos(tag: Tag) extends Table[NFTCollectionMetadata](tag, name) {
    def contract: Rep[Address.Contract] = column[Address.Contract]("contract", O.PrimaryKey)
    def collectionUri: Rep[String]      = column[String]("collection_uri")

    def * : ProvenShape[NFTCollectionMetadata] =
      (contract, collectionUri)
        .<>((NFTCollectionMetadata.apply _).tupled, NFTCollectionMetadata.unapply)
  }

  val table: TableQuery[TokenInfos] = TableQuery[TokenInfos]
}
