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

import sttp.tapir.{Codec, DecodeResult}
import sttp.tapir.Codec.PlainCodec

import org.alephium.api.TapirCodecs
import org.alephium.explorer.api.model.IntervalType
import org.alephium.json.Json._
import org.alephium.protocol.model.Address

object Codecs extends TapirCodecs {
  implicit val explorerAddressTapirCodec: PlainCodec[Address] =
    fromJson[Address]

  @SuppressWarnings(
    Array("org.wartremover.warts.JavaSerializable",
          "org.wartremover.warts.Product",
          "org.wartremover.warts.Serializable")) // Wartremover is complaining, maybe beacause of tapir macros
  implicit val timeIntervalCodec: PlainCodec[IntervalType] =
    Codec.derivedEnumeration[String, IntervalType](
      IntervalType.validate,
      _.string
    )

  def explorerFromJson[A: ReadWriter]: PlainCodec[A] =
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
