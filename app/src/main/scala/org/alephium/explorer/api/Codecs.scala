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

import scala.util.{Failure, Success, Try}

import sttp.tapir.{Codec, DecodeResult, Validator}
import sttp.tapir.CodecFormat.TextPlain

import org.alephium.explorer.{BlockHash, Hash}
import org.alephium.explorer.api.Json._
import org.alephium.explorer.api.model.{Address, BlockEntry, Transaction}
import org.alephium.json.Json._
import org.alephium.util.TimeStamp

object Codecs {
  private val hashTapirCodec: Codec[String, Hash, TextPlain] =
    fromJson[Hash]

  private val blockHashTapirCodec: Codec[String, BlockHash, TextPlain] =
    fromJson[BlockHash]

  implicit val timestampTapirCodec: Codec[String, TimeStamp, TextPlain] =
    Codec.long.validate(Validator.min(0L)).map(TimeStamp.unsafe(_))(_.millis)

  implicit val addressTapirCodec: Codec[String, Address, TextPlain] =
    fromJson[Address]

  implicit val blockEntryHashTapirCodec: Codec[String, BlockEntry.Hash, TextPlain] =
    blockHashTapirCodec.map(new BlockEntry.Hash(_))(_.value)

  implicit val transactionHashTapirCodec: Codec[String, Transaction.Hash, TextPlain] =
    hashTapirCodec.map(new Transaction.Hash(_))(_.value)

  def fromJson[A: ReadWriter]: Codec[String, A, TextPlain] =
    Codec.string.mapDecode[A] { raw =>
      Try(read[A](ujson.Str(raw))) match {
        case Success(a) => DecodeResult.Value(a)
        case Failure(error) =>
          DecodeResult.Error(raw, new IllegalArgumentException(error.getMessage))
      }
    } { a =>
      writeJs(a) match {
        case ujson.Str(str) => str
        case other          => write(other)
      }
    }
}
