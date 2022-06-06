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

import org.scalacheck.Gen

import org.alephium.explorer.GenCommon._
import org.alephium.protocol.model.NetworkId

/** Generators for types supplied by Core `org.alephium.protocol` package */
object GenCoreProtocol {

  val genNetworkId: Gen[NetworkId] =
    genBytePositive.map(NetworkId(_))

  def genNetworkId(exclude: NetworkId): Gen[NetworkId] =
    genNetworkId.filter(_ != exclude)
}
