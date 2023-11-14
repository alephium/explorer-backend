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
import org.alephium.explorer.api.model._
import org.alephium.protocol.model.{Address, TokenId}

// scalastyle:off magic.number
trait TokensEndpoints extends BaseEndpoint with QueryParams {

  lazy val maxSizeTokens: Int = 80

  private val tokensEndpoint =
    baseEndpoint
      .tag("Tokens")
      .in("tokens")

  val listTokens: BaseEndpoint[(Pagination, Option[StdInterfaceId]), ArraySeq[TokenInfo]] =
    tokensEndpoint.get
      .in(pagination)
      .in(tokenInterfaceIdQuery)
      .out(jsonBody[ArraySeq[TokenInfo]])
      .description("List token information")

  val listTokenInfo: BaseEndpoint[ArraySeq[TokenId], ArraySeq[TokenInfo]] =
    tokensEndpoint.post
      .in(jsonBody[ArraySeq[TokenId]].validate(Validator.maxSize(maxSizeTokens)))
      .out(jsonBody[ArraySeq[TokenInfo]])
      .description("list given tokens information")

  val listTokenTransactions: BaseEndpoint[(TokenId, Pagination), ArraySeq[Transaction]] =
    tokensEndpoint.get
      .in(path[TokenId]("token_id"))
      .in("transactions")
      .in(pagination)
      .out(jsonBody[ArraySeq[Transaction]])
      .description("List token transactions")

  val listTokenAddresses: BaseEndpoint[(TokenId, Pagination), ArraySeq[Address]] =
    tokensEndpoint.get
      .in(path[TokenId]("token_id"))
      .in("addresses")
      .in(pagination)
      .out(jsonBody[ArraySeq[Address]])
      .description("List token addresses")

  val listFungibleTokenMetadata: BaseEndpoint[ArraySeq[TokenId], ArraySeq[FungibleTokenMetadata]] =
    tokensEndpoint.post
      .in("fungible-metadata")
      .in(jsonBody[ArraySeq[TokenId]].validate(Validator.maxSize(maxSizeTokens)))
      .out(jsonBody[ArraySeq[FungibleTokenMetadata]])
      .description(
        "Return metadata for the given fungible tokens, if metadata doesn't exist or token isn't a fungible, it won't be in the output list"
      )

  val listNFTMetadata: BaseEndpoint[ArraySeq[TokenId], ArraySeq[NFTMetadata]] =
    tokensEndpoint.post
      .in("nft-metadata")
      .in(jsonBody[ArraySeq[TokenId]].validate(Validator.maxSize(maxSizeTokens)))
      .out(jsonBody[ArraySeq[NFTMetadata]])
      .description(
        "Return metadata for the given nft tokens, if metadata doesn't exist or token isn't a nft, it won't be in the output list"
      )

  val listNFTCollectionMetadata: BaseEndpoint[ArraySeq[Address], ArraySeq[NFTCollectionMetadata]] =
    tokensEndpoint.post
      .in("nft-collection-metadata")
      .in(jsonBody[ArraySeq[Address]].validate(Validator.maxSize(maxSizeTokens)))
      .out(jsonBody[ArraySeq[NFTCollectionMetadata]])
      .description(
        "Return metadata for the given nft collection addresses, if metadata doesn't exist or address isn't a nft collection, it won't be in the output list"
      )

  val getTotalTransactionsByToken: BaseEndpoint[TokenId, Int] =
    tokensEndpoint.get
      .in(path[TokenId]("token_id"))
      .in("total-transactions")
      .out(jsonBody[Int])
      .description("Get total transactions of a given token")

}
