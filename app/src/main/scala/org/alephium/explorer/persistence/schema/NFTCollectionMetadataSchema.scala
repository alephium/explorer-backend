// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.schema

import org.apache.pekko.util.ByteString
import slick.jdbc.PostgresProfile.api._
import slick.lifted.ProvenShape

import org.alephium.explorer.persistence.model.NFTCollectionMetadataEntity
import org.alephium.explorer.persistence.schema.CustomJdbcTypes._
import org.alephium.protocol.model.Address

object NFTCollectionMetadataSchema
    extends SchemaMainChain[NFTCollectionMetadataEntity]("nft_collection_metadata") {

  class TokenInfos(tag: Tag) extends Table[NFTCollectionMetadataEntity](tag, name) {
    def contract: Rep[Address.Contract] = column[Address.Contract]("contract", O.PrimaryKey)
    def collectionUri: Rep[ByteString]  = column[ByteString]("collection_uri")

    def * : ProvenShape[NFTCollectionMetadataEntity] =
      (contract, collectionUri)
        .<>((NFTCollectionMetadataEntity.apply _).tupled, NFTCollectionMetadataEntity.unapply)
  }

  val table: TableQuery[TokenInfos] = TableQuery[TokenInfos]
}
