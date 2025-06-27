// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.model

import org.alephium.protocol.model.Address
import org.alephium.protocol.model.TokenId
import org.alephium.util.U256

final case class TokenHolderEntity(
    address: Address,
    tokenId: TokenId,
    balance: U256
)
