// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.model

import org.apache.pekko.util.ByteString

import org.alephium.explorer.api.model.FungibleTokenMetadata
import org.alephium.protocol.model.TokenId
import org.alephium.util.U256

// Preserve arbitrary contract bytes until constructing the public API representation.
final case class FungibleTokenMetadataEntity(
    id: TokenId,
    symbol: ByteString,
    name: ByteString,
    decimals: U256
) {
  def toApi(): FungibleTokenMetadata =
    FungibleTokenMetadata(id, symbol.utf8String, name.utf8String, decimals)
}
