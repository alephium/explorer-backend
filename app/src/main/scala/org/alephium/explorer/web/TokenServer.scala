// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.web

import scala.collection.immutable.ArraySeq
import scala.concurrent.{ExecutionContext, Future}

import io.vertx.ext.web._
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import org.alephium.explorer.api.TokensEndpoints
import org.alephium.explorer.service.{HolderService, TokenService}
import org.alephium.protocol.model.Address

class TokenServer(tokenService: TokenService, holderService: HolderService)(implicit
    val executionContext: ExecutionContext,
    dc: DatabaseConfig[PostgresProfile]
) extends Server
    with TokensEndpoints {

  val routes: ArraySeq[Router => Route] =
    ArraySeq(
      route(listTokens.serverLogicSuccess[Future] { case (pagination, interfaceIdOpt) =>
        tokenService
          .listTokens(pagination, interfaceIdOpt)
      }),
      route(listTokenTransactions.serverLogicSuccess[Future] { case (token, pagination) =>
        tokenService
          .listTokenTransactions(token, pagination)
      }),
      route(listTokenAddresses.serverLogicSuccess[Future] { case (token, pagination) =>
        tokenService
          .listTokenAddresses(token, pagination)
      }),
      route(listTokenInfo.serverLogicSuccess[Future] { tokens =>
        tokenService.listTokenInfo(tokens)
      }),
      route(listFungibleTokenMetadata.serverLogicSuccess[Future] { tokens =>
        tokenService.listFungibleTokenMetadata(tokens)
      }),
      route(listNFTMetadata.serverLogicSuccess[Future] { tokens =>
        tokenService.listNFTMetadata(tokens)
      }),
      route(listNFTCollectionMetadata.serverLogicSuccess[Future] { addresses =>
        val contracts = addresses.collect { case address: Address.Contract => address }
        tokenService.listNFTCollectionMetadata(contracts)
      }),
      route(getAlphHolders.serverLogicSuccess[Future] { pagination =>
        holderService.getAlphHolders(pagination)
      }),
      route(getTokenHolders.serverLogicSuccess[Future] { case (tokenId, pagination) =>
        holderService.getTokenHolders(tokenId, pagination)
      })
    )
}
