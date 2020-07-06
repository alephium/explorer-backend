package org.alephium.explorer.api.model

import io.circe.{Codec, Decoder, Encoder}

final class Height(val value: Int) extends AnyVal

object Height {
  def unsafe(value: Int): Height = new Height(value)
  def from(value: Int): Either[String, Height] =
    if (value < 0) Left(s"height cannot be negative ($value)")
    else Right(Height.unsafe(value))

  val zero: Height = Height.unsafe(0)

  implicit val codec: Codec[Height] =
    Codec.from(Decoder.decodeInt.emap(from), Encoder.encodeInt.contramap(_.value))

  implicit val ordering: Ordering[Height] = Ordering.by[Height, Int](_.value)
}
