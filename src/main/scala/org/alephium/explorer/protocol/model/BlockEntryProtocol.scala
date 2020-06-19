package org.alephium.explorer.protocol.model

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

import org.alephium.explorer.Hash
import org.alephium.explorer.api.Circe.hashCodec
import org.alephium.explorer.api.model._
import org.alephium.rpc.CirceUtils.{avectorCodec, timestampCodec}
import org.alephium.util.{AVector, TimeStamp}

final case class BlockEntryProtocol(
    hash: Hash,
    timestamp: TimeStamp,
    chainFrom: GroupIndex,
    chainTo: GroupIndex,
    height: Height,
    deps: AVector[Hash],
    transactions: AVector[TransactionProtocol]
) {
  lazy val toApi: BlockEntry =
    BlockEntry(
      hash,
      timestamp,
      chainFrom,
      chainTo,
      height,
      deps,
      transactions.map(_.toApi)
    )
}

object BlockEntryProtocol {
  implicit val codec: Codec[BlockEntryProtocol] = deriveCodec[BlockEntryProtocol]
}
