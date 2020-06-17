package org.alephium.explorer.persistence.model

import org.alephium.explorer.Hash
import org.alephium.explorer.api.model.Input

final case class InputEntity(
    txHash: Hash,
    shortKey: Int,
    txHashRef: Hash,
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
  def fromApi(input: Input, txHash: Hash): InputEntity =
    InputEntity(
      txHash,
      input.shortKey,
      input.txHash,
      input.outputIndex
    )
}
