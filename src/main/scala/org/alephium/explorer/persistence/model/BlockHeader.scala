package org.alephium.explorer.persistence.model

import org.alephium.explorer.api.model.{BlockEntry, GroupIndex, Height, Transaction}
import org.alephium.util.{AVector, TimeStamp}

final case class BlockHeader(
    hash: BlockEntry.Hash,
    timestamp: Long,
    chainFrom: GroupIndex,
    chainTo: GroupIndex,
    height: Height,
    mainChain: Boolean
) {
  def toApi(deps: AVector[BlockEntry.Hash], transactions: AVector[Transaction]): BlockEntry =
    BlockEntry(hash,
               TimeStamp.unsafe(timestamp),
               chainFrom,
               chainTo,
               height,
               deps,
               transactions,
               mainChain)
}

object BlockHeader {
  def fromEntity(blockEntity: BlockEntity): BlockHeader =
    BlockHeader(
      blockEntity.hash,
      blockEntity.timestamp.millis,
      blockEntity.chainFrom,
      blockEntity.chainTo,
      blockEntity.height,
      blockEntity.mainChain
    )
}
