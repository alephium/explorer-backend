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

import org.alephium.crypto.HashSchema
import org.alephium.json.Json._
import org.alephium.serde.RandomBytes
import org.alephium.util.Hex

abstract class HashCompanion[A <: RandomBytes, H](hashAlgo: HashSchema[A])(
    fromHash: A => H,
    toHash: H   => A)(implicit aReadWriter: ReadWriter[A]) {
  def unsafe(value: String): H = fromHash(hashAlgo.unsafe(Hex.unsafe(value)))
  def from(value: String): Either[String, H] =
    Hex
      .from(value)
      .flatMap(hashAlgo.from)
      .toRight(s"Cannot decode hash: $value")
      .map(fromHash(_))

  implicit val codec: ReadWriter[H] =
    ReadWriter.join(aReadWriter.map(fromHash), aReadWriter.comap(toHash))
}
