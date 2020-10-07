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
