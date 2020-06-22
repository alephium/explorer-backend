package org.alephium.explorer.persistence.model

import org.alephium.explorer.Hash
import org.alephium.explorer.api.model.BlockEntry

final case class TransactionEntity(
    hash: Hash,
    blockHash: BlockEntry.Hash
)
