package org.alephium.explorer.api.model

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

final case class Input(
    outputRef: Output.Ref,
    unlockScript: String,
    txHashRef: Option[Transaction.Hash],
    address: Option[Address],
    amount: Option[Long]
)

object Input {
  implicit val codec: Codec[Input] = deriveCodec[Input]
}
