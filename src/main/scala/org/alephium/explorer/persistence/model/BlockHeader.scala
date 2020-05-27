package org.alephium.explorer.persistence.model

import org.alephium.explorer.api.model.BlockEntry
import org.alephium.util.{AVector, TimeStamp}

final case class BlockHeader(
    hash: String,
    timestamp: Long,
    chainFrom: Int,
    chainTo: Int,
    height: Int
) {
  def toApi(deps: AVector[String]): BlockEntry =
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
