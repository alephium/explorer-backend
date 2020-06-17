package org.alephium.explorer.api.model

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

import org.alephium.explorer.Hash
import org.alephium.explorer.api.Circe.hashCodec

final case class Input(
    shortKey: Int,
    txHash: Hash,
    outputIndex: Int
)

object Input {
  implicit val codec: Codec[Input] = deriveCodec[Input]
}
