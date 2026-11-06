// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.web

import scala.collection.immutable.ArraySeq
import scala.concurrent.{ExecutionContext, Future}

import org.scalacheck.Gen
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import org.alephium.explorer._
import org.alephium.explorer.ConfigDefaults._
import org.alephium.explorer.GenApiModel._
import org.alephium.explorer.GenCoreProtocol.{addressContractProtocolGen, contractIdGen}
import org.alephium.explorer.HttpFixture._
import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence.DatabaseFixtureForAll
import org.alephium.explorer.service._
import org.alephium.protocol.model.{Address, TokenId}
import org.alephium.util.U256

@SuppressWarnings(Array("org.wartremover.warts.Var"))
class TokenServerSpec()
    extends AlephiumFutureSpec
    with HttpServerFixture
    with DatabaseFixtureForAll {

  private val tokenInfos = ArraySeq.from(
    List(
      TokenInfo(
        tokenIdGen.sample.get,
        Some(StdInterfaceId.FungibleToken.default),
        Some("0001"),
        Some("0001")
      ),
      TokenInfo(
        tokenIdGen.sample.get,
        Some(StdInterfaceId.NFT.default),
        Some("0003"),
        Some("0003")
      )
    )
  )

  private val tokenTransactions = ArraySeq.from(Gen.listOfN(2, transactionGen).sample.get)
  private val tokenAddresses    = ArraySeq.from(Gen.listOfN(2, addressGen).sample.get)
  private val fungibleMetadata = ArraySeq(
    FungibleTokenMetadata(tokenIdGen.sample.get, "alph", "Alephium", U256.Zero)
  )
  private val nftMetadata = ArraySeq(
    NFTMetadata(tokenIdGen.sample.get, "ipfs://nft", contractIdGen.sample.get, U256.Zero)
  )
  private val nftCollectionMetadata = ArraySeq(
    NFTCollectionMetadata(addressContractProtocolGen.sample.get, "ipfs://collection")
  )
  val holdertokens = ArraySeq.from(Gen.listOf(holderInfoGen).sample.get)

  val tokenService = new EmptyTokenService {
    override def listTokens(pagination: Pagination, interfaceIdOpt: Option[StdInterfaceId])(implicit
        ec: ExecutionContext,
        dc: DatabaseConfig[PostgresProfile]
    ): Future[ArraySeq[TokenInfo]] =
      Future.successful(tokenInfos)

    override def listTokenTransactions(token: TokenId, pagination: Pagination)(implicit
        ec: ExecutionContext,
        dc: DatabaseConfig[PostgresProfile]
    ): Future[ArraySeq[Transaction]] =
      Future.successful(tokenTransactions)

    override def listTokenInfo(tokens: ArraySeq[TokenId])(implicit
        ec: ExecutionContext,
        dc: DatabaseConfig[PostgresProfile]
    ): Future[ArraySeq[TokenInfo]] =
      Future.successful(tokenInfos)

    override def listTokenAddresses(token: TokenId, pagination: Pagination)(implicit
        dc: DatabaseConfig[PostgresProfile]
    ): Future[ArraySeq[Address]] =
      Future.successful(tokenAddresses)

    override def listFungibleTokenMetadata(tokens: ArraySeq[TokenId])(implicit
        dc: DatabaseConfig[PostgresProfile]
    ): Future[ArraySeq[FungibleTokenMetadata]] =
      Future.successful(fungibleMetadata)

    override def listNFTMetadata(tokens: ArraySeq[TokenId])(implicit
        dc: DatabaseConfig[PostgresProfile]
    ): Future[ArraySeq[NFTMetadata]] =
      Future.successful(nftMetadata)

    override def listNFTCollectionMetadata(addresses: ArraySeq[Address.Contract])(implicit
        dc: DatabaseConfig[PostgresProfile]
    ): Future[ArraySeq[NFTCollectionMetadata]] =
      Future.successful(nftCollectionMetadata)
  }

  val holderService = new EmptyHolderService {
    override def getAlphHolders(pagination: Pagination)(implicit
        ec: ExecutionContext,
        dc: DatabaseConfig[PostgresProfile]
    ): Future[ArraySeq[HolderInfo]] = Future.successful(holdertokens)

    override def getTokenHolders(token: TokenId, pagination: Pagination)(implicit
        ec: ExecutionContext,
        dc: DatabaseConfig[PostgresProfile]
    ): Future[ArraySeq[HolderInfo]] = Future.successful(holdertokens)
  }

  val tokenServer =
    new TokenServer(tokenService, holderService)

  val routes = tokenServer.routes

  "return alph holders" in {
    Get(s"/tokens/holders/alph") check { response =>
      response.as[ArraySeq[HolderInfo]] is holdertokens
    }
  }

  "return token holders" in {
    Get(s"/tokens/holders/token/${tokenIdGen.sample.get.toHexString}") check { response =>
      response.as[ArraySeq[HolderInfo]] is holdertokens
    }
  }

  "return token routes" in {
    val tokenIds     = ArraySeq.from(List(tokenIdGen.sample.get, tokenIdGen.sample.get))
    val tokenId      = tokenIds.head
    val contract     = addressContractProtocolGen.sample.get
    val tokenIdsJson = tokenIds.map(id => s""""${id.toHexString}"""").mkString("[", ",", "]")
    val contractJson = s"""["${contract.toString}"]"""
    val expectedAddressesJson =
      tokenAddresses.map(address => s""""${address.toString}"""").mkString("[", ",", "]")

    Get("/tokens") check { response =>
      response.as[ArraySeq[TokenInfo]] is tokenInfos
    }

    Get(s"/tokens/${tokenId.toHexString}/transactions") check { response =>
      response.as[ArraySeq[Transaction]] is tokenTransactions
    }

    Get(s"/tokens/${tokenId.toHexString}/addresses") check { response =>
      (response.body.toOption.get == expectedAddressesJson) is true
    }

    Post("/tokens", tokenIdsJson) check { response =>
      response.as[ArraySeq[TokenInfo]] is tokenInfos
    }

    Post("/tokens/fungible-metadata", tokenIdsJson) check { response =>
      response.as[ArraySeq[FungibleTokenMetadata]] is fungibleMetadata
    }

    Post("/tokens/nft-metadata", tokenIdsJson) check { response =>
      response.as[ArraySeq[NFTMetadata]] is nftMetadata
    }

    Post("/tokens/nft-collection-metadata", contractJson) check { response =>
      response.as[ArraySeq[NFTCollectionMetadata]] is nftCollectionMetadata
    }
  }
}
