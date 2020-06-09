package org.alephium.explorer.persistence.model

import org.alephium.explorer.Hash
import org.alephium.explorer.api.model.BlockEntry
import org.alephium.util.{AVector, TimeStamp}

final case class BlockHeader(
    hash: Hash,
    timestamp: Long,
    chainFrom: Int,
    chainTo: Int,
    height: Int
) {
  def toApi(deps: AVector[Hash]): BlockEntry =
    BlockEntry(hash, TimeStamp.unsafe(timestamp), chainFrom, chainTo, height, deps)
}

object BlockHeader {
  def fromApi(blockEntry: BlockEntry): BlockHeader =
    BlockHeader(
      blockEntry.hash,
      blockEntry.timestamp.millis,
      blockEntry.chainFrom,
      blockEntry.chainTo,
      blockEntry.height
    )
}
