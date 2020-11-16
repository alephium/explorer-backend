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

package org.alephium.explorer.protocol.model

import io.circe.{Codec, Decoder, Encoder}
import io.circe.generic.semiauto.deriveCodec

import org.alephium.api.CirceUtils._
import org.alephium.explorer.Hash
import org.alephium.explorer.api.Circe.{hashCodec, u256Codec}
import org.alephium.explorer.api.model.{Address, Output, Transaction}
import org.alephium.explorer.persistence.model.OutputEntity
import org.alephium.serde._
import org.alephium.util.{Bytes, TimeStamp, U256}

final case class OutputProtocol(
    amount: U256,
    createdHeight: Int,
    address: Address
) {
  def toEntity(txHash: Transaction.Hash, index: Int, timestamp: TimeStamp): OutputEntity = {
    OutputEntity(
      txHash,
      amount,
      createdHeight,
      address,
      Hash.hash(txHash.value.bytes ++ Bytes.from(index)),
      timestamp,
      spent = None
    )
  }
}

object OutputProtocol {

  final case class Ref(scriptHint: Int, key: Hash) {
    def toApi: Output.Ref = Output.Ref(scriptHint, key)
  }

  object Ref {
    implicit val codec: Codec[Ref] = deriveCodec[Ref]
  }
  implicit val codec: Codec[OutputProtocol] = deriveCodec[OutputProtocol]

  def fromSerde[T: Serde]: Codec[T] = {
    def encoder: Encoder[T] = byteStringEncoder.contramap(serialize[T])
    val decoder: Decoder[T] = byteStringDecoder.emap(deserialize[T](_).left.map(_.getMessage))

    Codec.from(decoder, encoder)
  }
}
