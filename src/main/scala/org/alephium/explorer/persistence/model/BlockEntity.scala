package org.alephium.explorer.persistence.model

import org.alephium.explorer.api.model.{BlockEntry, GroupIndex, Height}
import org.alephium.util.{AVector, TimeStamp}

final case class BlockEntity(
    hash: BlockEntry.Hash,
    timestamp: TimeStamp,
    chainFrom: GroupIndex,
    chainTo: GroupIndex,
    height: Height,
    deps: AVector[BlockEntry.Hash],
    transactions: AVector[TransactionEntity],
    inputs: AVector[InputEntity],
    outputs: AVector[OutputEntity]
)
