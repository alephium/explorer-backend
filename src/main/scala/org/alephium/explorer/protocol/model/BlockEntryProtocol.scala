package org.alephium.explorer.protocol.model

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence.model._
import org.alephium.rpc.CirceUtils.{avectorCodec, timestampCodec}
import org.alephium.util.{AVector, TimeStamp}

final case class BlockEntryProtocol(
    hash: BlockEntry.Hash,
    timestamp: TimeStamp,
    chainFrom: GroupIndex,
    chainTo: GroupIndex,
    height: Height,
    deps: AVector[BlockEntry.Hash],
    transactions: AVector[TransactionProtocol]
) {
  lazy val toEntity: BlockEntity =
    BlockEntity(
      hash,
      timestamp,
      chainFrom,
      chainTo,
      height,
      deps,
      transactions.map(_.toEntity(hash, timestamp)),
      transactions.flatMap(tx => tx.inputs.map(_.toEntity(tx.hash))),
      transactions.flatMap(tx =>
        tx.outputs.mapWithIndex { case (out, index) => out.toEntity(tx.hash, index, timestamp) }),
      mainChain = false
    )
}

object BlockEntryProtocol {
  implicit val codec: Codec[BlockEntryProtocol] = deriveCodec[BlockEntryProtocol]
}
