// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.api.model

import sttp.tapir.Schema

import org.alephium.api.TapirSchemas._
import org.alephium.explorer.api.Json._
import org.alephium.explorer.api.Schemas._
import org.alephium.json.Json._
import org.alephium.protocol.model.TokenId
import org.alephium.util.U256

final case class FungibleTokenMetadata(
    id: TokenId,
    symbol: String,
    name: String,
    decimals: U256
)

object FungibleTokenMetadata {
  implicit val readWriter: ReadWriter[FungibleTokenMetadata] = macroRW
  implicit val schema: Schema[FungibleTokenMetadata]         = Schema.derived[FungibleTokenMetadata]
}
