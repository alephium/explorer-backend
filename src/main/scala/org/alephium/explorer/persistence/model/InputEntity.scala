package org.alephium.explorer.persistence.model

import org.alephium.explorer.api.model.{Input, Transaction}

final case class InputEntity(
    txHash: Transaction.Hash,
    shortKey: Int,
    txHashRef: Transaction.Hash,
    outputIndex: Int
) {
  lazy val toApi: Input =
    Input(
      shortKey,
      txHashRef,
      outputIndex
    )
}

object InputEntity {
  def fromApi(input: Input, txHash: Transaction.Hash): InputEntity =
    InputEntity(
      txHash,
      input.shortKey,
      input.txHash,
      input.outputIndex
    )
}
