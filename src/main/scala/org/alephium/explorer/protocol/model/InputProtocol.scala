package org.alephium.explorer.protocol.model

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

import org.alephium.explorer.Hash
import org.alephium.explorer.api.Circe.hashCodec
import org.alephium.explorer.api.model.Input

final case class InputProtocol(
    shortKey: Int,
    txHash: Hash,
    outputIndex: Int
) {
  lazy val toApi: Input =
    Input(
      shortKey,
      txHash,
      outputIndex
    )
}

object InputProtocol {
  implicit val codec: Codec[InputProtocol] = deriveCodec[InputProtocol]
}
