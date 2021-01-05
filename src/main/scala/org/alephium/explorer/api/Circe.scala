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

import io.circe.{Codec, Decoder, Encoder, Json}

import org.alephium.api.CirceUtils.byteStringDecoder
import org.alephium.explorer.{BlockHash, Hash}
import org.alephium.util.U256

object Circe {
  implicit val hashEncoder: Encoder[Hash] = hash => Json.fromString(hash.toHexString)
  implicit val hashDecoder: Decoder[Hash] =
    byteStringDecoder.emap(Hash.from(_).toRight("cannot decode hash"))
  implicit val hashCodec: Codec[Hash] = Codec.from(hashDecoder, hashEncoder)

  implicit val blockHashEncoder: Encoder[BlockHash] = hash => Json.fromString(hash.toHexString)
  implicit val blockHashDecoder: Decoder[BlockHash] =
    byteStringDecoder.emap(BlockHash.from(_).toRight("cannot decode block hash"))
  implicit val blockHashCodec: Codec[BlockHash] = Codec.from(blockHashDecoder, blockHashEncoder)

  implicit val u256Encoder: Encoder[U256] = Encoder.encodeJavaBigInteger.contramap[U256](_.toBigInt)
  implicit val u256Decoder: Decoder[U256] = Decoder.decodeJavaBigInteger.emap { u256 =>
    U256.from(u256).toRight(s"Invalid U256: $u256")
  }
  implicit val u256Codec: Codec[U256] = Codec.from(u256Decoder, u256Encoder)
}
