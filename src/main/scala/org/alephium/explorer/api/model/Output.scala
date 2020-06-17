package org.alephium.explorer.api.model

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

final case class Output(
    value: Long,
    pubScript: String
)

object Output {
  implicit val codec: Codec[Output] = deriveCodec[Output]
}
