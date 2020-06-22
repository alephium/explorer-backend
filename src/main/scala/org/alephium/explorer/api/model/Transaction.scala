package org.alephium.explorer.api.model

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

import org.alephium.explorer
import org.alephium.explorer.HashCompanion
import org.alephium.rpc.CirceUtils.avectorCodec
import org.alephium.util.AVector

final case class Transaction(
    hash: Transaction.Hash,
    inputs: AVector[Input],
    outputs: AVector[Output]
)

object Transaction {
  final class Hash(val value: explorer.Hash) extends AnyVal
  object Hash                                extends HashCompanion[Hash](new Hash(_), _.value)

  implicit val codec: Codec[Transaction] = deriveCodec[Transaction]
}
