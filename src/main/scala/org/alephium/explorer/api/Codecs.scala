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

import io.circe
import io.circe.syntax._
import sttp.tapir.{Codec, DecodeResult, Validator}
import sttp.tapir.CodecFormat.TextPlain

import org.alephium.explorer.Hash
import org.alephium.explorer.api.Circe.{hashCodec => circeHashCodec}
import org.alephium.explorer.api.model.{Address, BlockEntry, Transaction}
import org.alephium.util.{TimeStamp}

object Codecs {
  implicit val timestampCodec: Codec[String, TimeStamp, TextPlain] =
    Codec.long.validate(Validator.min(0L)).map(TimeStamp.unsafe(_))(_.millis)

  implicit val hashCodec: Codec[String, Hash, TextPlain] =
    fromCirce[Hash]

  implicit val addressCodec: Codec[String, Address, TextPlain] =
    fromCirce[Address]

  implicit val blockHashCodec: Codec[String, BlockEntry.Hash, TextPlain] =
    hashCodec.map(new BlockEntry.Hash(_))(_.value)

  implicit val transactionHashCodec: Codec[String, Transaction.Hash, TextPlain] =
    hashCodec.map(new Transaction.Hash(_))(_.value)

  private def fromCirce[A: circe.Codec]: Codec[String, A, TextPlain] =
    Codec.string.mapDecode[A] { raw =>
      raw.asJson.as[A] match {
        case Right(a) => DecodeResult.Value(a)
        case Left(error) =>
          DecodeResult.Error(raw, new IllegalArgumentException(error.getMessage))
      }
    }(_.asJson.toString)

}
