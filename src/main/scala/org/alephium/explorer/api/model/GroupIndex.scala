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

final class GroupIndex(val value: Int) extends AnyVal {
  override def toString(): String = value.toString
}

object GroupIndex {
  def unsafe(value: Int): GroupIndex = new GroupIndex(value)
  def from(value: Int): Either[String, GroupIndex] =
    if (value < 0) {
      Left(s"group index cannot be negative ($value)")
    } else {
      Right(GroupIndex.unsafe(value))
    }

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  implicit val readWriter: ReadWriter[GroupIndex] =
    readwriter[Int].bimap[GroupIndex](_.value,
                                      int =>
                                        from(int) match {
                                          case Right(groupIndex) => groupIndex
                                          case Left(error)       => throw new Abort(error)
                                      })
}
