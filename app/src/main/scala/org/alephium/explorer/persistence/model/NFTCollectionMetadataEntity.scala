// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.model

import org.apache.pekko.util.ByteString

import org.alephium.explorer.api.model.NFTCollectionMetadata
import org.alephium.protocol.model.Address

// Preserve arbitrary contract bytes until constructing the public API representation.
final case class NFTCollectionMetadataEntity(
    address: Address.Contract,
    collectionUri: ByteString
) {
  def toApi(): NFTCollectionMetadata = NFTCollectionMetadata(address, collectionUri.utf8String)
}
