// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.api.model

import sttp.tapir._
import upickle.core.Abort

import org.alephium.json.Json._
import org.alephium.util.Hex

sealed trait StdInterfaceId {
  def value: String
  def id: String
  def category: String
}

sealed trait TokenStdInterfaceId extends StdInterfaceId

object StdInterfaceId {

  final case class FungibleToken(id: String) extends TokenStdInterfaceId {
    val value: String    = FungibleToken.value
    val category: String = FungibleToken.category
  }

  object FungibleToken {
    val category: String       = "0001"
    val value: String          = "fungible"
    val default: FungibleToken = FungibleToken(category)
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

  final case class NFT(id: String) extends TokenStdInterfaceId {
    val value: String    = NFT.value
    val category: String = NFT.category
  }

  object NFT {
    val value: String    = "non-fungible"
    val category: String = "0003"
    val default: NFT     = NFT(category)
  }

  final case class Unknown(id: String) extends TokenStdInterfaceId {
    val value: String    = id
    val category: String = id.take(4)
  }

  case object NonStandard extends TokenStdInterfaceId {
    val value: String    = "non-standard"
    val id: String       = ""
    val category: String = ""
  }

  def from(code: String): StdInterfaceId =
    code match {
      case id if id.startsWith(FungibleToken.category) => FungibleToken(id)
      case id if id.startsWith(NFT.category)           => NFT(id)
      case NFTCollection.id                            => NFTCollection
      case NFTCollectionWithRoyalty.id                 => NFTCollectionWithRoyalty
      case ""                                          => NonStandard
      case unknown                                     => Unknown(unknown)
    }

  def validate(str: String): Option[StdInterfaceId] =
    str match {
      case FungibleToken.value            => Some(FungibleToken.default)
      case NFTCollection.value            => Some(NFTCollection)
      case NFTCollectionWithRoyalty.value => Some(NFTCollectionWithRoyalty)
      case NFT.value                      => Some(NFT.default)
      case NonStandard.value              => Some(NonStandard)
      case ""                             => Some(NonStandard)
      case other =>
        if (other.sizeIs >= 4 && other.sizeIs <= 16 && Hex.from(other).isDefined) {
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

  implicit val tokenReadWriter: ReadWriter[TokenStdInterfaceId] = readwriter[StdInterfaceId].bimap(
    identity,
    {
      case token: TokenStdInterfaceId => token
      case other: StdInterfaceId =>
        throw new Abort(
          s"Not a token std interface: ${other.value}"
        )
    }
  )

  @SuppressWarnings(
    Array(
      "org.wartremover.warts.JavaSerializable",
      "org.wartremover.warts.Product",
      "org.wartremover.warts.Serializable"
    )
  )
  val stdInterfaceIds: Seq[StdInterfaceId] =
    Seq(FungibleToken.default, NFTCollection, NFTCollectionWithRoyalty, NFT.default, NonStandard)

  @SuppressWarnings(
    Array(
      "org.wartremover.warts.JavaSerializable",
      "org.wartremover.warts.Product",
      "org.wartremover.warts.Serializable"
    )
  )
  val tokenStdInterfaceIds: Seq[TokenStdInterfaceId] =
    Seq(FungibleToken.default, NFT.default, NonStandard)

  val schema: Schema[StdInterfaceId] = Schema.string
    .name(Schema.SName("StdInterfaceId"))
    .validate(Validator.enumeration(stdInterfaceIds.toList, v => Some(v.value)))

  val tokenSchema: Schema[TokenStdInterfaceId] = Schema.string
    .name(Schema.SName("TokenStdInterfaceId"))
    .validate(Validator.enumeration(tokenStdInterfaceIds.toList, v => Some(v.value)))

  val tokenWithHexStringSchema: Schema[TokenStdInterfaceId] =
    Schema[TokenStdInterfaceId](
      SchemaType.SCoproduct(
        List(
          StdInterfaceId.tokenSchema,
          Schema.string.format("hex-string").description("Raw interface id, e.g. 0001")
        ),
        None
      )(_ => None)
    )
}
