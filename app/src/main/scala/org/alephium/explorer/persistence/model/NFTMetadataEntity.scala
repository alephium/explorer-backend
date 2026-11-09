// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.model

import org.apache.pekko.util.ByteString

import org.alephium.explorer.api.model.NFTMetadata
import org.alephium.protocol.model.{ContractId, TokenId}
import org.alephium.util.U256

// Preserve arbitrary contract bytes until constructing the public API representation.
final case class NFTMetadataEntity(
    id: TokenId,
    tokenUri: ByteString,
    collectionId: ContractId,
    nftIndex: U256
) {
  def toApi(): NFTMetadata = NFTMetadata(id, tokenUri.utf8String, collectionId, nftIndex)
}
