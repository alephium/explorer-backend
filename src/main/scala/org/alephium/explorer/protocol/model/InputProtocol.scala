package org.alephium.explorer.protocol.model

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

import org.alephium.explorer.api.model.{Input, Transaction}

final case class InputProtocol(
    shortKey: Int,
    txHash: Transaction.Hash,
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
