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
import org.alephium.serde._
import org.alephium.util.Base58

final class Address(val value: String) extends AnyVal {
  override def toString(): String = value
}

object Address {
  def unsafe(value: String): Address = new Address(value)

  implicit val writer: Writer[Address] =
    StringWriter.comap[Address](_.value)

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  implicit val reader: Reader[Address] =
    StringReader.map {
      Base58
        .decode(_)
        .map(bs => Address.unsafe(Base58.encode(bs)))
        .getOrElse(throw new Abort(s"cannot decode to base58"))
    }

  implicit val readWriter: ReadWriter[Address] = ReadWriter.join(reader, writer)

  implicit val serde: Serde[Address] = stringSerde.xmap(Address.unsafe, _.value)
}
