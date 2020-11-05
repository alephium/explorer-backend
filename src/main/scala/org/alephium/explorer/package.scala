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

package org.alephium

import java.math.BigDecimal

import org.alephium.crypto.Blake2b
import org.alephium.protocol.ALF
import org.alephium.util.U256

package object explorer {
  @inline @specialized def sideEffect[E](effect: E): Unit = {
    val _ = effect
    () //Return unit to prevent warning due to discarding value
  }

  @SuppressWarnings(Array("org.wartremover.warts.Equals"))
  implicit final class AnyOps[A](val self: A) extends AnyVal {
    def ===(other: A): Boolean = self == other
    def =/=(other: A): Boolean = self != other
  }

  type Hash = Blake2b
  val Hash: Blake2b.type = Blake2b

  def alfCoinConvertion(u256: U256): BigDecimal =
    new BigDecimal(u256.toBigInt).divide(new BigDecimal(ALF.CoinInOneALF.toBigInt))
}
