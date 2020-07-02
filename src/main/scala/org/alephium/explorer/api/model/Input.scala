package org.alephium.explorer.api.model

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

final case class Input(
    outputRef: Output.Ref,
    unlockScript: String
)

object Input {
  implicit val codec: Codec[Input] = deriveCodec[Input]
}
