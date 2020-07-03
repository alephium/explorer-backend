package org.alephium.explorer.persistence.model

import org.alephium.explorer.Hash
import org.alephium.explorer.api.model.{Address, Output, Transaction}

final case class OutputEntity(
    txHash: Transaction.Hash,
    amount: Long,
    createdHeight: Int,
    address: Address,
    outputRefKey: Hash
) {
  lazy val toApi: Output =
    Output(
      amount,
      createdHeight,
      address
    )
}
