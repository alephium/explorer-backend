// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.model

import org.alephium.protocol.model.{Address, BlockHash, TokenId, TransactionId}
import org.alephium.util.TimeStamp

final case class TokenTxPerAddressEntity(
    address: Address,
    grouplessAddress: Option[GrouplessAddress],
    hash: TransactionId,
    blockHash: BlockHash,
    timestamp: TimeStamp,
    txOrder: Int,
    mainChain: Boolean,
    token: TokenId
)
