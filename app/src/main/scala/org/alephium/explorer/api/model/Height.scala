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

import upickle.core.Abort

import org.alephium.json.Json._
import org.alephium.protocol.ALPH

final class Height(val value: Int) extends AnyVal {
  override def toString(): String = value.toString
}

object Height {
  def unsafe(value: Int): Height = new Height(value)
  def from(value: Int): Either[String, Height] =
    if (value < 0) {
      Left(s"height cannot be negative ($value)")
    } else {
      Right(Height.unsafe(value))
    }

  val genesis: Height = Height.unsafe(ALPH.GenesisHeight)

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  implicit val readWriter: ReadWriter[Height] =
    readwriter[Int].bimap[Height](
      _.value,
      int =>
        from(int) match {
          case Right(height) => height
          case Left(error)   => throw new Abort(error)
        }
    )

  implicit val ordering: Ordering[Height] = Ordering.by[Height, Int](_.value)
}
