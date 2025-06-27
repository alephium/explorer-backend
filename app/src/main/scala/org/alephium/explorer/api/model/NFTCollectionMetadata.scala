// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.api.model

import sttp.tapir.Schema

import org.alephium.api.TapirSchemas._
import org.alephium.explorer.api.Json._
import org.alephium.json.Json._
import org.alephium.protocol.model.Address

final case class NFTCollectionMetadata(
    address: Address.Contract,
    collectionUri: String
)

object NFTCollectionMetadata {
  implicit val readWriter: ReadWriter[NFTCollectionMetadata] = macroRW
  implicit val schema: Schema[NFTCollectionMetadata]         = Schema.derived[NFTCollectionMetadata]
}
