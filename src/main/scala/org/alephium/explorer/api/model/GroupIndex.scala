package org.alephium.explorer.api.model

import io.circe.{Codec, Decoder, Encoder}

final class GroupIndex(val value: Int) extends AnyVal

object GroupIndex {
  def unsafe(value: Int): GroupIndex = new GroupIndex(value)
  def from(value: Int): Either[String, GroupIndex] =
    if (value < 0) Left(s"group index cannot be negative ($value)")
    else Right(GroupIndex.unsafe(value))

  implicit val codec: Codec[GroupIndex] =
    Codec.from(Decoder.decodeInt.emap(from), Encoder.encodeInt.contramap(_.value))
}
