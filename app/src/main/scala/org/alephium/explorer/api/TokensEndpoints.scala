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

import scala.collection.immutable.ArraySeq

import sttp.tapir._
import sttp.tapir.generic.auto._

import org.alephium.api.Endpoints.jsonBody
import org.alephium.explorer.api.EndpointExamples._
import org.alephium.explorer.api.Schemas.tokenIdSchema
import org.alephium.explorer.api.model._
import org.alephium.protocol.model.{Address, TokenId}

// scalastyle:off magic.number
trait TokensEndpoints extends BaseEndpoint with QueryParams {

  lazy val maxSizeTokens: Int             = 80
  lazy val maxSizeAddressesForTokens: Int = maxSizeTokens

  private val tokensEndpoint =
    baseEndpoint
      .tag("Tokens")
      .in("tokens")

  val listTokens: BaseEndpoint[(Pagination, Option[TokenStdInterfaceId]), ArraySeq[TokenInfo]] =
    tokensEndpoint.get
      .in(pagination)
      .in(tokenInterfaceIdQuery)
      .out(jsonBody[ArraySeq[TokenInfo]])
      .summary("List token information")

  val listTokenInfo: BaseEndpoint[ArraySeq[TokenId], ArraySeq[TokenInfo]] =
    tokensEndpoint.post
      .in(arrayBody[TokenId]("token ids", maxSizeTokens))
      .out(jsonBody[ArraySeq[TokenInfo]])
      .summary("List given tokens information")

  val listTokenTransactions: BaseEndpoint[(TokenId, Pagination), ArraySeq[Transaction]] =
    tokensEndpoint.get
      .in(path[TokenId]("token_id"))
      .in("transactions")
      .in(pagination)
      .out(jsonBody[ArraySeq[Transaction]])
      .summary("List token transactions")

  val listTokenAddresses: BaseEndpoint[(TokenId, Pagination), ArraySeq[Address]] =
    tokensEndpoint.get
      .in(path[TokenId]("token_id"))
      .in("addresses")
      .in(pagination)
      .out(jsonBody[ArraySeq[Address]])
      .summary("List token addresses")

  val listFungibleTokenMetadata: BaseEndpoint[ArraySeq[TokenId], ArraySeq[FungibleTokenMetadata]] =
    tokensEndpoint.post
      .in("fungible-metadata")
      .in(arrayBody[TokenId]("token ids", maxSizeTokens))
      .out(jsonBody[ArraySeq[FungibleTokenMetadata]])
      .summary(
        "Return metadata for the given fungible tokens"
      )
      .description(
        "If metadata doesn't exist or token isn't a fungible, it won't be in the output list"
      )

  val listNFTMetadata: BaseEndpoint[ArraySeq[TokenId], ArraySeq[NFTMetadata]] =
    tokensEndpoint.post
      .in("nft-metadata")
      .in(arrayBody[TokenId]("token ids", maxSizeTokens))
      .out(jsonBody[ArraySeq[NFTMetadata]])
      .summary(
        "Return metadata for the given nft tokens"
      )
      .description("if metadata doesn't exist or token isn't a nft, it won't be in the output list")

  val listNFTCollectionMetadata: BaseEndpoint[ArraySeq[Address], ArraySeq[NFTCollectionMetadata]] =
    tokensEndpoint.post
      .in("nft-collection-metadata")
      .in(arrayBody[Address]("addresses", maxSizeAddressesForTokens))
      .out(jsonBody[ArraySeq[NFTCollectionMetadata]])
      .summary(
        "Return metadata for the given nft collection addresses"
      )
      .description(
        "if metadata doesn't exist or address isn't a nft collection, it won't be in the output list"
      )

  val getAlphHolders: BaseEndpoint[Pagination, ArraySeq[HolderInfo]] =
    tokensEndpoint.get
      .in("holders")
      .in("alph")
      .in(pagination)
      .out(jsonBody[ArraySeq[HolderInfo]])
      .summary("Get a sorted list of top addresses by ALPH balance")
      .description("Updates once per day.")

  val getTokenHolders: BaseEndpoint[(TokenId, Pagination), ArraySeq[HolderInfo]] =
    tokensEndpoint.get
      .in("holders")
      .in("token")
      .in(path[TokenId]("token_id"))
      .in(pagination)
      .out(jsonBody[ArraySeq[HolderInfo]])
      .summary(
        "Get a sorted list of top addresses by {token_id} balance. Updates once per day."
      )
      .description("Updates once per day.")
}
