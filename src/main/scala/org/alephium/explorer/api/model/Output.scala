package org.alephium.explorer.api.model

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

import org.alephium.explorer.Hash
import org.alephium.explorer.api.Circe.hashCodec

final case class Output(
    address: Hash,
    value: Long
)

object Output {
  implicit val codec: Codec[Output] = deriveCodec[Output]
}
