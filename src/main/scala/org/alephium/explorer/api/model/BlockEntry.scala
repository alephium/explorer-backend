package org.alephium.explorer.api.model

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

import org.alephium.explorer
import org.alephium.explorer.HashCompanion
import org.alephium.rpc.CirceUtils.{avectorCodec, timestampCodec}
import org.alephium.util.{AVector, TimeStamp}

final case class BlockEntry(
    hash: BlockEntry.Hash,
    timestamp: TimeStamp,
    chainFrom: GroupIndex,
    chainTo: GroupIndex,
    height: Height,
    deps: AVector[BlockEntry.Hash],
    transactions: AVector[Transaction],
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

object BlockEntry {

  final class Hash(val value: explorer.Hash) extends AnyVal {
    override def toString(): String = value.toHexString
  }

  object Hash extends HashCompanion[Hash](new Hash(_), _.value)

  implicit val codec: Codec[BlockEntry] = deriveCodec[BlockEntry]
}
