package org.alephium.explorer.persistence.model

import org.alephium.explorer.Hash
import org.alephium.explorer.api.model.{Output, Transaction}
import org.alephium.util.DjbHash

final case class OutputEntity(
    txHash: Transaction.Hash,
    value: Long,
    address: Hash,
    shortKey: Int
) {
  lazy val toApi: Output =
    Output(
      address,
      value
    )
}

object OutputEntity {
  def fromApi(output: Output, txHash: Transaction.Hash): OutputEntity =
    OutputEntity(
      txHash,
      output.value,
      output.address,
      DjbHash.intHash(output.address.bytes)
    )
}
