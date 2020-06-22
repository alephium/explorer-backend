package org.alephium.explorer

import io.circe.Codec

import org.alephium.explorer.api.Circe.{hashDecoder, hashEncoder}
import org.alephium.util.Hex

abstract class HashCompanion[H](fromHash: Hash => H, toHash: H => Hash) {
  def unsafe(value: String): H = fromHash(Hash.unsafe(Hex.unsafe(value)))
  def from(value: String): Either[String, H] =
    Hex
      .from(value)
      .flatMap(Hash.from)
      .toRight(s"Cannot decode hash: $value")
      .map(fromHash(_))

  implicit val codec: Codec[H] =
    Codec.from(hashDecoder.map(fromHash), hashEncoder.contramap(toHash))
}
