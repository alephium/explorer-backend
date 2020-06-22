package org.alephium.explorer.api.model

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

final case class Input(
    shortKey: Int,
    txHash: Transaction.Hash,
    outputIndex: Int
)

object Input {
  implicit val codec: Codec[Input] = deriveCodec[Input]
}
