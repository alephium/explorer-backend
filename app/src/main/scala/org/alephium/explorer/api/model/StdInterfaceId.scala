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

import scala.collection.immutable.ArraySeq

import sttp.tapir.{Schema, Validator}
import upickle.core.Abort

import org.alephium.json.Json._

sealed trait StdInterfaceId {
  def value: String
  def id: Int
}

object StdInterfaceId {

  case object FungibleToken extends StdInterfaceId {
    val value: String = "fungible"
    val id: Int       = 1
  }

  case object NFTCollection extends StdInterfaceId {
    val value: String = "non-fungible-collection"
    val id: Int       = 2
  }

  case object NFT extends StdInterfaceId {
    val value: String = "non-fungible"
    val id: Int       = 3
  }

  case object NonStandard extends StdInterfaceId {
    val value: String = "non-standard"
    val id: Int       = 0
  }

  def from(code: String): StdInterfaceId =
    code match {
      case "0001" => FungibleToken
      case "0002" => NFTCollection
      case "0003" => NFT
      case _      => NonStandard
    }

  def unsafeFromId(id: Int): StdInterfaceId =
    id match {
      case 1 => FungibleToken
      case 2 => NFTCollection
      case 3 => NFT
      case 0 => NonStandard
    }
  @SuppressWarnings(
    Array(
      "org.wartremover.warts.JavaSerializable",
      "org.wartremover.warts.Product",
      "org.wartremover.warts.Serializable"
    )
  ) // Wartremover is complaining, don't now why :/
  val stdInterfaceIds: ArraySeq[StdInterfaceId] =
    ArraySeq(FungibleToken, NFTCollection, NFT, NonStandard)

  def validate(str: String): Option[StdInterfaceId] =
    str match {
      case FungibleToken.value => Some(FungibleToken)
      case NFTCollection.value => Some(NFTCollection)
      case NFT.value           => Some(NFT)
      case NonStandard.value   => Some(NonStandard)
      case _                   => None
    }
  implicit val levelReadWriter: ReadWriter[StdInterfaceId] = readwriter[String].bimap(
    _.value,
    { str =>
      validate(str).getOrElse(
        throw new Abort(
          s"Cannot decode interface id, expected one of: ${stdInterfaceIds.map(_.value)}"
        )
      )
    }
  )

  implicit def schema: Schema[StdInterfaceId] =
    Schema.string.validate(Validator.enumeration(stdInterfaceIds.toList).encode(_.value))
}
