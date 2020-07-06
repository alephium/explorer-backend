package org.alephium.explorer.api.model

import io.circe.{Codec, Decoder, Encoder}

import org.alephium.util.Base58

final class Address(val value: String) extends AnyVal {
  override def toString(): String = value
}

object Address {
  def unsafe(value: String): Address = new Address(value)

  implicit val encoder: Encoder[Address] =
    Encoder.encodeString.contramap[Address](_.value)

  implicit val decoder: Decoder[Address] =
    Decoder.decodeString.emap {
      Base58
        .decode(_)
        .map(bs => Address.unsafe(Base58.encode(bs)))
        .toRight(s"cannot decode to base58")
    }

  implicit val codec: Codec[Address] = Codec.from(decoder, encoder)
}
