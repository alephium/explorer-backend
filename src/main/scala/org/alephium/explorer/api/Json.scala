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

package org.alephium.explorer.api

import upickle.core.Abort

import org.alephium.api.UtilJson._
import org.alephium.explorer.{BlockHash, Hash}
import org.alephium.json.Json._
import org.alephium.util.U256

@SuppressWarnings(Array("org.wartremover.warts.Throw"))
object Json {
  implicit val hashWriter: Writer[Hash] = StringWriter.comap[Hash](
    _.toHexString
  )
  implicit val hashReader: Reader[Hash] =
    byteStringReader.map(Hash.from(_).getOrElse(throw new Abort("cannot decode hash")))
  implicit val hashReadWriter: ReadWriter[Hash] = ReadWriter.join(hashReader, hashWriter)

  implicit val blockHashWriter: Writer[BlockHash] = StringWriter.comap[BlockHash](
    _.toHexString
  )
  implicit val blockHashReader: Reader[BlockHash] =
    byteStringReader.map(BlockHash.from(_).getOrElse(throw new Abort("cannot decode block hash")))

  implicit val blockHashReadWriter: ReadWriter[BlockHash] =
    ReadWriter.join(blockHashReader, blockHashWriter)

  implicit val u256Writer: Writer[U256] = javaBigIntegerWriter.comap[U256](_.toBigInt)
  implicit val u256Reader: Reader[U256] = javaBigIntegerReader.map { u256 =>
    U256.from(u256).getOrElse(throw new Abort(s"Invalid U256: $u256"))
  }

  implicit val u256ReadWriter: ReadWriter[U256] = ReadWriter.join(u256Reader, u256Writer)
}
