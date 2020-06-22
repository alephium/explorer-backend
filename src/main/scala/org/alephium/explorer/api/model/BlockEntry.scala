package org.alephium.explorer.api.model

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

import org.alephium.explorer
import org.alephium.explorer.api.Circe.{hashDecoder, hashEncoder}
import org.alephium.rpc.CirceUtils.{avectorCodec, timestampCodec}
import org.alephium.util.{AVector, Hex, TimeStamp}

final case class BlockEntry(
    hash: BlockEntry.Hash,
    timestamp: TimeStamp,
    chainFrom: GroupIndex,
    chainTo: GroupIndex,
    height: Height,
    deps: AVector[BlockEntry.Hash],
    transactions: AVector[Transaction]
)

object BlockEntry {

  final class Hash(val value: explorer.Hash) extends AnyVal

  object Hash {
    def unsafe(value: String): Hash = new Hash(explorer.Hash.unsafe(Hex.unsafe(value)))
    def from(value: String): Either[String, Hash] =
      Hex
        .from(value)
        .flatMap(explorer.Hash.from)
        .toRight(s"Cannot decode hash: $value")
        .map(new Hash(_))
    implicit val codec: Codec[Hash] =
      Codec.from(hashDecoder.map(new Hash(_)), hashEncoder.contramap(_.value))
  }

  implicit val codec: Codec[BlockEntry] = deriveCodec[BlockEntry]
}
