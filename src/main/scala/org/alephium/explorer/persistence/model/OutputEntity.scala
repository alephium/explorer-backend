package org.alephium.explorer.persistence.model

import org.alephium.explorer.api.model.{Address, Output, Transaction}

final case class OutputEntity(
    txHash: Transaction.Hash,
    amount: Long,
    createdHeight: Int,
    address: Address
) {
  lazy val toApi: Output =
    Output(
      amount,
      createdHeight,
      address
    )
}

object OutputEntity {
  def fromApi(output: Output, txHash: Transaction.Hash): OutputEntity =
    OutputEntity(
      txHash,
      output.amount,
      output.createdHeight,
      output.address
    )
}
