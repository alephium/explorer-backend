// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.model

import org.alephium.protocol.model.{BlockHash, TokenId, TransactionId}
import org.alephium.util.TimeStamp

final case class TransactionPerTokenEntity(
    hash: TransactionId,
    blockHash: BlockHash,
    token: TokenId,
    timestamp: TimeStamp,
    txOrder: Int,
    mainChain: Boolean
)
