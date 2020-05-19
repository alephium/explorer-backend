package org.alephium.explorer.api.model

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

import org.alephium.rpc.CirceUtils.{avectorCodec, timestampCodec}
import org.alephium.util.{AVector, TimeStamp}

final case class BlockEntry(
    hash: String,
    timestamp: TimeStamp,
    chainFrom: Int,
    chainTo: Int,
    height: Int,
    deps: AVector[String]
)

object BlockEntry {
  implicit val codec: Codec[BlockEntry] = deriveCodec[BlockEntry]
}
