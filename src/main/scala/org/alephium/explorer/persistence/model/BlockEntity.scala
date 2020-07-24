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
    outputs: AVector[OutputEntity],
    mainChain: Boolean
) {
  def parent(groupNum: Int): Option[BlockEntry.Hash] =
    if (isGenesis) {
      None
    } else {
      deps.takeRight(groupNum).get(chainTo.value)
    }

  lazy val isGenesis: Boolean = height === Height.zero
}
