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

package org.alephium.explorer.web

import scala.collection.immutable.ArraySeq
import scala.concurrent.{ExecutionContext, Future}

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

  @SuppressWarnings(
    Array(
      "org.wartremover.warts.JavaSerializable",
      "org.wartremover.warts.Product",
      "org.wartremover.warts.Serializable"
    )
  )
  def endpointsLogic: ArraySeq[EndpointLogic] = ArraySeq(
    listTokens.serverLogicSuccess[Future] { case (pagination, interfaceIdOpt) =>
      tokenService
        .listTokens(pagination, interfaceIdOpt)
    },
    listTokenTransactions.serverLogicSuccess[Future] { case (token, pagination) =>
      tokenService
        .listTokenTransactions(token, pagination)
    },
    listTokenAddresses.serverLogicSuccess[Future] { case (token, pagination) =>
      tokenService
        .listTokenAddresses(token, pagination)
    },
    listTokenInfo.serverLogicSuccess[Future] { tokens =>
      tokenService.listTokenInfo(tokens)
    },
    listFungibleTokenMetadata.serverLogicSuccess[Future] { tokens =>
      tokenService.listFungibleTokenMetadata(tokens)
    },
    listNFTMetadata.serverLogicSuccess[Future] { tokens =>
      tokenService.listNFTMetadata(tokens)
    },
    listNFTCollectionMetadata.serverLogicSuccess[Future] { addresses =>
      val contracts = addresses.collect { case address: Address.Contract => address }
      tokenService.listNFTCollectionMetadata(contracts)
    },
    getAlphHolders.serverLogicSuccess[Future] { pagination =>
      holderService.getAlphHolders(pagination)
    },
    getTokenHolders.serverLogicSuccess[Future] { case (tokenId, pagination) =>
      holderService.getTokenHolders(tokenId, pagination)
    }
  )
}
