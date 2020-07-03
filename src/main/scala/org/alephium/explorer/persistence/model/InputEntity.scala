package org.alephium.explorer.persistence.model

import org.alephium.explorer.Hash
import org.alephium.explorer.api.model.{Input, Output, Transaction}

final case class InputEntity(
    txHash: Transaction.Hash,
    scriptHint: Int,
    key: Hash,
    unlockScript: String
) {
  def toApi(outputRef: Option[OutputEntity]): Input =
    Input(
      Output.Ref(scriptHint, key),
      unlockScript,
      outputRef.map(_.txHash),
      outputRef.map(_.address),
      outputRef.map(_.amount)
    )
}
