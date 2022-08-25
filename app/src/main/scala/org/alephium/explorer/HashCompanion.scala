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

import org.alephium.json.Json._
import org.alephium.serde._

abstract class HashCompanion[A <: RandomBytes, H](fromHash: A => H, toHash: H => A)(
    implicit aReadWriter: ReadWriter[A],
    aSerde: Serde[A]) {

  implicit val codec: ReadWriter[H] =
    ReadWriter.join(aReadWriter.map(fromHash), aReadWriter.comap(toHash))

  implicit val serde: Serde[H] = aSerde.xmap(fromHash, toHash)
}
