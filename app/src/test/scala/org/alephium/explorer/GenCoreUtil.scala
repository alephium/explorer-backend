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

import org.alephium.protocol.ALPH
import org.alephium.util.{Number, TimeStamp, U256}

/** Generators for types supplied by Core `org.alephium.util` package */
object GenCoreUtil {

  // java.sql.timestamp is out of range with Long.MaxValue
  val timestampMaxValue: TimeStamp = TimeStamp.unsafe(253370764800000L) // Jan 01 9999 00:00:00

  val u256Gen: Gen[U256] = Gen.posNum[Long].map(U256.unsafe)
  val timestampGen: Gen[TimeStamp] =
    Gen.choose[Long](0, timestampMaxValue.millis).map(TimeStamp.unsafe)
  val amountGen: Gen[U256] = Gen.choose(1000L, Number.quadrillion).map(ALPH.nanoAlph)
}
