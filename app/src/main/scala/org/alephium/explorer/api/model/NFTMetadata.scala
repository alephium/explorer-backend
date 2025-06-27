// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.api.model

import sttp.tapir.Schema

import org.alephium.api.TapirSchemas._
import org.alephium.explorer.api.Json._
import org.alephium.explorer.api.Schemas._
import org.alephium.json.Json._
import org.alephium.protocol.model.{ContractId, TokenId}
import org.alephium.util.U256

final case class NFTMetadata(
    id: TokenId,
    tokenUri: String,
    collectionId: ContractId,
    nftIndex: U256
)

object NFTMetadata {
  implicit val readWriter: ReadWriter[NFTMetadata] = macroRW
  implicit val schema: Schema[NFTMetadata]         = Schema.derived[NFTMetadata]
}
