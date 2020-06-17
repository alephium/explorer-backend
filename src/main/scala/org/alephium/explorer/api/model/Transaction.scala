package org.alephium.explorer.api.model

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

import org.alephium.explorer.Hash
import org.alephium.explorer.api.Circe.hashCodec
import org.alephium.rpc.CirceUtils.avectorCodec
import org.alephium.util.AVector

final case class Transaction(
    hash: Hash,
    inputs: AVector[Input],
    outputs: AVector[Output]
)

object Transaction {
  implicit val codec: Codec[Transaction] = deriveCodec[Transaction]
}
