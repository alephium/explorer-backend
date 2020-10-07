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
