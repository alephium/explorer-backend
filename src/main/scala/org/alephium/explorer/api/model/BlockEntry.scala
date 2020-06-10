package org.alephium.explorer.api.model

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

import org.alephium.explorer.Hash
import org.alephium.explorer.api.Circe.hashCodec
import org.alephium.rpc.CirceUtils.{avectorCodec, timestampCodec}
import org.alephium.util.{AVector, TimeStamp}

final case class BlockEntry(
    hash: Hash,
    timestamp: TimeStamp,
    chainFrom: GroupIndex,
    chainTo: GroupIndex,
    height: Int,
    deps: AVector[Hash]
)

object BlockEntry {
  implicit val codec: Codec[BlockEntry] = deriveCodec[BlockEntry]
}
