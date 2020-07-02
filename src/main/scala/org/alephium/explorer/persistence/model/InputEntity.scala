package org.alephium.explorer.persistence.model

import org.alephium.explorer.Hash
import org.alephium.explorer.api.model.{Input, Output, Transaction}

final case class InputEntity(
    txHash: Transaction.Hash,
    scriptHint: Int,
    key: Hash,
    unlockScript: String
) {
  lazy val toApi: Input =
    Input(
      Output.Ref(scriptHint, key),
      unlockScript
    )
}

object InputEntity {
  def fromApi(input: Input, txHash: Transaction.Hash): InputEntity =
    InputEntity(
      txHash,
      input.outputRef.scriptHint,
      input.outputRef.key,
      input.unlockScript
    )
}
