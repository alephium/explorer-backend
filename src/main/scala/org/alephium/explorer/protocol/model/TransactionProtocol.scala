package org.alephium.explorer.protocol.model

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

import org.alephium.explorer.Hash
import org.alephium.explorer.api.Circe.hashCodec
import org.alephium.explorer.api.model.Transaction
import org.alephium.rpc.CirceUtils.avectorCodec
import org.alephium.util.AVector

final case class TransactionProtocol(
    hash: Hash,
    inputs: AVector[InputProtocol],
    outputs: AVector[OutputProtocol]
) {
  lazy val toApi: Transaction =
    Transaction(
      hash,
      inputs.map(_.toApi),
      outputs.map(_.toApi)
    )
}

object TransactionProtocol {
  implicit val codec: Codec[TransactionProtocol] = deriveCodec[TransactionProtocol]
}
