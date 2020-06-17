package org.alephium.explorer.persistence.model

import org.alephium.explorer.Hash
import org.alephium.explorer.api.model.Output

final case class OutputEntity(
    txHash: Hash,
    value: Long,
    pubScript: String
) {
  lazy val toApi: Output =
    Output(
      value,
      pubScript
    )
}

object OutputEntity {
  def fromApi(output: Output, txHash: Hash): OutputEntity =
    OutputEntity(
      txHash,
      output.value,
      output.pubScript
    )
}
