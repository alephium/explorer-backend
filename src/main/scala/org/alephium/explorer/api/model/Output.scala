package org.alephium.explorer.api.model

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

import org.alephium.explorer.Hash
import org.alephium.explorer.api.Circe.hashCodec

final case class Output(
    amount: Long,
    createdHeight: Int,
    address: Address
)

object Output {
  final case class Ref(scriptHint: Int, key: Hash)

  object Ref {
    implicit val codec: Codec[Ref] = deriveCodec[Ref]
  }

  implicit val codec: Codec[Output] = deriveCodec[Output]
}
