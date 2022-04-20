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

package org.alephium.explorer.persistence.model

import akka.util.ByteString

import org.alephium.serde
import org.alephium.serde._
import org.alephium.util.TimeStamp

final case class AppState(
    key: String,
    value: AppState.Value
)
object AppState {

  trait Value
  final case class Time(value: TimeStamp) extends Value

  object Value extends Serde[AppState.Value] {

    override def serialize(input: Value): ByteString =
      input match {
        case ts: Time => ByteString(0) ++ serde.serialize(ts.value)
      }

    override def _deserialize(input: ByteString): SerdeResult[Staging[Value]] =
      byteSerde._deserialize(input).flatMap {
        case Staging(byte, rest) =>
          if (byte == 0) {
            serdeTS.deserialize(rest).map(ts => Staging(Time(ts), ByteString.empty))
          } else {
            Left(SerdeError.wrongFormat(s"Invalid AppState.Value"))
          }
      }
  }
}
