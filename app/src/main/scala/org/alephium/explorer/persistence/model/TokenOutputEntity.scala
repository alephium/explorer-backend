// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.model

import akka.util.ByteString

import org.alephium.protocol.Hash
import org.alephium.protocol.model.{Address, BlockHash, TokenId, TransactionId}
import org.alephium.util.{TimeStamp, U256}

final case class TokenOutputEntity(
    blockHash: BlockHash,
    txHash: TransactionId,
    timestamp: TimeStamp,
    outputType: OutputEntity.OutputType,
    hint: Int,
    key: Hash,
    token: TokenId,
    amount: U256,
    address: Address,
    grouplessAddress: Option[GrouplessAddress],
    mainChain: Boolean,
    lockTime: Option[TimeStamp],
    message: Option[ByteString],
    outputOrder: Int,
    txOrder: Int,
    spentFinalized: Option[TransactionId],
    spentTimestamp: Option[TimeStamp]
)
