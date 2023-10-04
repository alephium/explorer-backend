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

import sttp.tapir.Schema
import upickle.core.Abort

import org.alephium.json.Json._
import org.alephium.util.Hex

sealed trait StdInterfaceId {
  def value: String
  def id: String
  def category: String
}

object StdInterfaceId {

  case object FungibleToken extends StdInterfaceId {
    val value: String    = "fungible"
    val id: String       = "0001"
    val category: String = "0001"
  }

  case object NFTCollection extends StdInterfaceId {
    val value: String    = "non-fungible-collection"
    val id: String       = "0002"
    val category: String = "0002"
  }

  case object NFTCollectionWithRoyalty extends StdInterfaceId {
    val value: String    = "non-fungible-collection-with-royalty"
    val id: String       = "000201"
    val category: String = "0002"
  }

  case object NFT extends StdInterfaceId {
    val value: String    = "non-fungible"
    val id: String       = "0003"
    val category: String = "0003"
  }

  final case class Unknown(id: String) extends StdInterfaceId {
    val value: String    = s"$id"
    val category: String = ""
  }

  case object NonStandard extends StdInterfaceId {
    val value: String    = "non-standard"
    val id: String       = ""
    val category: String = ""
  }

  def from(code: String): StdInterfaceId =
    code match {
      case "0001"   => FungibleToken
      case "0002"   => NFTCollection
      case "0003"   => NFT
      case "000201" => NFTCollectionWithRoyalty
      case ""       => NonStandard
      case unknown  => Unknown(unknown)
    }

  def validate(str: String): Option[StdInterfaceId] =
    str match {
      case FungibleToken.value            => Some(FungibleToken)
      case NFTCollection.value            => Some(NFTCollection)
      case NFTCollectionWithRoyalty.value => Some(NFTCollectionWithRoyalty)
      case NFT.value                      => Some(NFT)
      case NonStandard.value              => Some(NonStandard)
      case ""                             => Some(NonStandard)
      case other =>
        if (other.sizeIs <= 16 && Hex.from(other).isDefined) {
          Some(Unknown(other))
        } else {
          None
        }
    }
  implicit val readWriter: ReadWriter[StdInterfaceId] = readwriter[String].bimap(
    _.value,
    { str =>
      validate(str).getOrElse(
        throw new Abort(
          s"Cannot decode interface id}"
        )
      )
    }
  )

  @SuppressWarnings(
    Array(
      "org.wartremover.warts.JavaSerializable",
      "org.wartremover.warts.Product",
      "org.wartremover.warts.Serializable"
    )
  ) // Wartremover is complaining, don't now why :/
  implicit val tokenSchema: Schema[StdInterfaceId] = Schema.string.description(
    s"${List(FungibleToken, NFT, NonStandard).map(_.value).mkString(", ")} or any interface id in hex-string format, e.g: 0001"
  )
}
