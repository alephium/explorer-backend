package org.alephium.explorer.protocol.model

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

import org.alephium.explorer.api.model.Input

final case class InputProtocol(
    outputRef: OutputProtocol.Ref,
    unlockScript: String
) {
  lazy val toApi: Input =
    Input(
      outputRef.toApi,
      unlockScript
    )
}

object InputProtocol {
  implicit val codec: Codec[InputProtocol] = deriveCodec[InputProtocol]
}
