package org.alephium.explorer.protocol.model

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

import org.alephium.explorer.api.model.{BlockEntry, Transaction}
import org.alephium.explorer.persistence.model.TransactionEntity
import org.alephium.rpc.CirceUtils.avectorCodec
import org.alephium.util.{AVector, TimeStamp}

final case class TransactionProtocol(
    hash: Transaction.Hash,
    inputs: AVector[InputProtocol],
    outputs: AVector[OutputProtocol]
) {
  def toEntity(blockHash: BlockEntry.Hash, timestamp: TimeStamp): TransactionEntity =
    TransactionEntity(
      hash,
      blockHash,
      timestamp
    )
}

object TransactionProtocol {
  implicit val codec: Codec[TransactionProtocol] = deriveCodec[TransactionProtocol]
}
