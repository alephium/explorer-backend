package org.alephium.explorer.persistence.model

import org.alephium.explorer.api.model.{BlockEntry, Transaction}

final case class TransactionEntity(
    hash: Transaction.Hash,
    blockHash: BlockEntry.Hash
)
