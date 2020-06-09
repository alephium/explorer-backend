package org.alephium.explorer.persistence.model

import org.alephium.explorer.Hash
import org.alephium.explorer.api.model.BlockEntry
import org.alephium.util.{AVector, Hex, TimeStamp}

final case class BlockHeader(
    hash: String,
    timestamp: Long,
    chainFrom: Int,
    chainTo: Int,
    height: Int
) {
  def toApi(deps: AVector[String]): BlockEntry =
    BlockEntry(Hash.unsafe(Hex.unsafe(hash)),
               TimeStamp.unsafe(timestamp),
               chainFrom,
               chainTo,
               height,
               deps)
}

object BlockHeader {
  def fromApi(blockEntry: BlockEntry): BlockHeader =
    BlockHeader(
      blockEntry.hash.toHexString,
      blockEntry.timestamp.millis,
      blockEntry.chainFrom,
      blockEntry.chainTo,
      blockEntry.height
    )
}
