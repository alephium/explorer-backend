// Copyright 2018 The Alephium Authors
// This file is part of the alephium project.
//
// The library is free software: you can redistribute it and/or modify
// it under the terms of the GNU Lesser General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// The library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public License
// along with the library. If not, see <http://www.gnu.org/licenses/>.

package org.alephium.explorer.api.model

import io.circe.{Codec, Decoder, Encoder}

final class Height(val value: Int) extends AnyVal {
  override def toString(): String = value.toString
}

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
