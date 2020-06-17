package org.alephium.explorer.persistence.model

import org.alephium.explorer.Hash

final case class TransactionEntity(
    hash: Hash,
    blockHash: Hash
)
