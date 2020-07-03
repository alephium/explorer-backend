package org.alephium.explorer.protocol.model

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

import org.alephium.explorer.api.model.Transaction
import org.alephium.explorer.persistence.model.InputEntity

final case class InputProtocol(
    outputRef: OutputProtocol.Ref,
    unlockScript: String
) {
  def toEntity(txHash: Transaction.Hash): InputEntity =
    InputEntity(
      txHash,
      outputRef.scriptHint,
      outputRef.key,
      unlockScript
    )
}

object InputProtocol {
  implicit val codec: Codec[InputProtocol] = deriveCodec[InputProtocol]
}
