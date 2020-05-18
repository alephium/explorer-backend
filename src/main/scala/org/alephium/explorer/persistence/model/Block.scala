package org.alephium.explorer.persistence.model

import org.alephium.explorer.api.model.BlockEntry
import org.alephium.util.{AVector, TimeStamp}

final case class Block(
    hash: String,
    timestamp: Long,
    chainFrom: Int,
    chainTo: Int,
    height: Int
) {
  def toApi: BlockEntry =
    BlockEntry(hash, TimeStamp.unsafe(timestamp), chainFrom, chainTo, height, AVector.empty)
}

object Block {
  def fromApi(blockEntry: BlockEntry): Block =
    Block(
      blockEntry.hash,
      blockEntry.timestamp.millis,
      blockEntry.chainFrom,
      blockEntry.chainTo,
      blockEntry.height
    )
}
