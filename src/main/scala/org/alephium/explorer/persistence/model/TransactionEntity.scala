package org.alephium.explorer.persistence.model

import org.alephium.explorer.api.model.{BlockEntry, Transaction}
import org.alephium.util.TimeStamp

final case class TransactionEntity(
    hash: Transaction.Hash,
    blockHash: BlockEntry.Hash,
    timestamp: TimeStamp
)
