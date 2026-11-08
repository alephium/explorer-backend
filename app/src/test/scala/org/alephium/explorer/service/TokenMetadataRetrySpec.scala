// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.service

import scala.collection.immutable.ArraySeq
import scala.concurrent.Future

import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.AlephiumFutureSpec
import org.alephium.explorer.ConfigDefaults._
import org.alephium.explorer.GenApiModel._
import org.alephium.explorer.GenDBModel._
import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence.{DatabaseFixtureForEach, TestDBRunner}
import org.alephium.explorer.persistence.model.TokenInfoEntity
import org.alephium.explorer.persistence.queries.{ContractQueries, TokenQueries}
import org.alephium.explorer.persistence.schema.TokenInfoSchema
import org.alephium.protocol.model.{Address, TokenId}
import org.alephium.util.TimeStamp

class TokenMetadataRetrySpec
    extends AlephiumFutureSpec
    with DatabaseFixtureForEach
    with TestDBRunner {

  private def addToken(token: TokenId): Unit = {
    exec(TokenInfoSchema.table += TokenInfoEntity(token, TimeStamp.zero, None, None))
    ()
  }

  private def addContracts(count: Int): ArraySeq[Address.Contract] = {
    val group  = groupIndexGen.sample.get
    val events = ArraySeq.fill(count)(createEventGen(group).sample.get)
    exec(ContractQueries.insertContractCreation(events, group))
    exec(ContractQueries.listContractWithoutInterfaceIdQuery())
  }

  "Token metadata retries" should {
    "retry unavailable interface and fungible metadata on later syncs and stop after success" in {
      val metadata = fungibleTokenMetadataGen.sample.get
      addToken(metadata.id)
      var guesses = 0
      var fetches = 0
      val client = new EmptyBlockFlowClient {
        override def guessTokenStdInterfaceId(token: TokenId): Future[Option[StdInterfaceId]] = {
          guesses += 1
          Future.successful(if (guesses == 1) None else Some(StdInterfaceId.FungibleToken.default))
        }
        override def fetchFungibleTokenMetadata(
            token: TokenId
        ): Future[Option[FungibleTokenMetadata]] = {
          fetches += 1
          Future.successful(if (fetches == 1) None else Some(metadata))
        }
      }
      (1 to 2).foreach { _ =>
        TokenService.updateTokensMetadata(client).futureValue is ()
        exec(TokenQueries.listTokenWithoutInterfaceIdQuery()) is ArraySeq(metadata.id)
        exec(TokenQueries.listFungibleTokenMetadataQuery(ArraySeq(metadata.id))) is ArraySeq.empty
      }
      TokenService.updateTokensMetadata(client).futureValue is ()
      exec(TokenQueries.listTokenWithoutInterfaceIdQuery()) is ArraySeq.empty
      exec(TokenQueries.listFungibleTokenMetadataQuery(ArraySeq(metadata.id))) is ArraySeq(metadata)
      TokenService.updateTokensMetadata(client).futureValue is ()
      guesses is 3
      fetches is 2
    }

    "retry unavailable NFT metadata without finalizing its interface prematurely" in {
      val metadata = nftMetadataGen.sample.get
      addToken(metadata.id)
      var fetches = 0
      val client = new EmptyBlockFlowClient {
        override def guessTokenStdInterfaceId(token: TokenId): Future[Option[StdInterfaceId]] =
          Future.successful(Some(StdInterfaceId.NFT.default))
        override def fetchNFTMetadata(token: TokenId): Future[Option[NFTMetadata]] = {
          fetches += 1
          Future.successful(if (fetches == 1) None else Some(metadata))
        }
      }
      TokenService.updateTokensMetadata(client).futureValue is ()
      exec(TokenQueries.listTokenWithoutInterfaceIdQuery()) is ArraySeq(metadata.id)
      TokenService.updateTokensMetadata(client).futureValue is ()
      exec(TokenQueries.listNFTMetadataQuery(ArraySeq(metadata.id))) is ArraySeq(metadata)
      exec(TokenQueries.listTokenWithoutInterfaceIdQuery()) is ArraySeq.empty
    }

    "retry unavailable collection interfaces and metadata, including royalty collections" in {
      Seq[StdInterfaceId](StdInterfaceId.NFTCollection, StdInterfaceId.NFTCollectionWithRoyalty)
        .foreach { interfaceId =>
          val contract = addContracts(1).head
          val metadata = NFTCollectionMetadata(contract, "https://example.com/collection.json")
          var guesses  = 0
          var fetches  = 0
          val client = new EmptyBlockFlowClient {
            override def guessStdInterfaceId(
                address: Address.Contract
            ): Future[Option[StdInterfaceId]] = {
              guesses += 1
              Future.successful(if (guesses == 1) None else Some(interfaceId))
            }
            override def fetchNFTCollectionMetadata(
                address: Address.Contract
            ): Future[Option[NFTCollectionMetadata]] = {
              fetches += 1
              Future.successful(if (fetches == 1) None else Some(metadata))
            }
          }
          (1 to 2).foreach { _ =>
            TokenService.updateContractsMetadata(client).futureValue is ()
            exec(ContractQueries.listContractWithoutInterfaceIdQuery()) is ArraySeq(contract)
            exec(TokenQueries.listNFTCollectionMetadataQuery(ArraySeq(contract))) is ArraySeq.empty
          }
          TokenService.updateContractsMetadata(client).futureValue is ()
          exec(ContractQueries.listContractWithoutInterfaceIdQuery()) is ArraySeq.empty
          exec(TokenQueries.listNFTCollectionMetadataQuery(ArraySeq(contract))) is ArraySeq(
            metadata
          )
          TokenService.updateContractsMetadata(client).futureValue is ()
          guesses is 3
          fetches is 2
        }
    }

    "recover previously classified tokens with missing metadata without resetting classification" in {
      val fungible = fungibleTokenMetadataGen.sample.get
      val nft      = nftMetadataGen.sample.get
      addToken(fungible.id)
      addToken(nft.id)
      exec(TokenQueries.updateTokenInterfaceId(fungible.id, StdInterfaceId.FungibleToken.default))
      exec(TokenQueries.updateTokenInterfaceId(nft.id, StdInterfaceId.NFT.default))
      var available = false
      val client = new EmptyBlockFlowClient {
        override def guessTokenStdInterfaceId(token: TokenId): Future[Option[StdInterfaceId]] = {
          val interfaceId: StdInterfaceId =
            if (token == fungible.id) {
              StdInterfaceId.FungibleToken.default
            } else {
              StdInterfaceId.NFT.default
            }
          Future.successful(Some(interfaceId))
        }
        override def fetchFungibleTokenMetadata(
            token: TokenId
        ): Future[Option[FungibleTokenMetadata]] =
          Future.successful(Option.when(available)(fungible))
        override def fetchNFTMetadata(token: TokenId): Future[Option[NFTMetadata]] =
          Future.successful(Option.when(available)(nft))
      }
      val before = exec(TokenQueries.listTokenInfosQuery(ArraySeq(fungible.id, nft.id))).toSet
      TokenService.updateTokensMetadata(client).futureValue is ()
      exec(TokenQueries.listTokenInfosQuery(ArraySeq(fungible.id, nft.id))).toSet is before
      exec(TokenQueries.listTokenWithoutInterfaceIdQuery()).toSet is Set(fungible.id, nft.id)
      available = true
      TokenService.updateTokensMetadata(client).futureValue is ()
      exec(TokenQueries.listTokenWithoutInterfaceIdQuery()) is ArraySeq.empty
      exec(TokenQueries.listFungibleTokenMetadataQuery(ArraySeq(fungible.id))) is ArraySeq(fungible)
      exec(TokenQueries.listNFTMetadataQuery(ArraySeq(nft.id))) is ArraySeq(nft)
    }

    "recover previously classified collections with missing metadata" in {
      Seq[StdInterfaceId](StdInterfaceId.NFTCollection, StdInterfaceId.NFTCollectionWithRoyalty)
        .foreach { interfaceId =>
          val contract = addContracts(1).head
          exec(ContractQueries.updateContractInterfaceId(contract, interfaceId))
          val metadata  = NFTCollectionMetadata(contract, "https://example.com/collection.json")
          var available = false
          val client = new EmptyBlockFlowClient {
            override def guessStdInterfaceId(
                address: Address.Contract
            ): Future[Option[StdInterfaceId]] =
              Future.successful(Some(interfaceId))
            override def fetchNFTCollectionMetadata(
                address: Address.Contract
            ): Future[Option[NFTCollectionMetadata]] =
              Future.successful(Option.when(available)(metadata))
          }
          TokenService.updateContractsMetadata(client).futureValue is ()
          exec(ContractQueries.listContractWithoutInterfaceIdQuery()) is ArraySeq(contract)
          available = true
          TokenService.updateContractsMetadata(client).futureValue is ()
          exec(ContractQueries.listContractWithoutInterfaceIdQuery()) is ArraySeq.empty
          exec(TokenQueries.listNFTCollectionMetadataQuery(ArraySeq(contract))) is ArraySeq(
            metadata
          )
        }
    }

    "exclude destroyed collections and unsupported collection variants from pending metadata" in {
      val group = groupIndexGen.sample.get
      val event = createEventGen(group).sample.get
      exec(ContractQueries.insertContractCreation(ArraySeq(event), group))
      val contract = exec(ContractQueries.listContractWithoutInterfaceIdQuery()).head
      exec(ContractQueries.updateContractInterfaceId(contract, StdInterfaceId.NFTCollection))
      exec(
        ContractQueries.updateContractDestruction(
          ArraySeq(destroyEventGen(contract).sample.get),
          group
        )
      )
      exec(ContractQueries.listContractWithoutInterfaceIdQuery()) is ArraySeq.empty
      val unsupported = addContracts(1).head
      exec(ContractQueries.updateContractInterfaceId(unsupported, StdInterfaceId.Unknown("000202")))
      exec(ContractQueries.listContractWithoutInterfaceIdQuery()) is ArraySeq.empty
    }

    "keep an explicitly detected nonstandard interface terminal" in {
      val token = tokenIdGen.sample.get
      addToken(token)
      addContracts(1)
      var tokenGuesses    = 0
      var contractGuesses = 0
      val client = new EmptyBlockFlowClient {
        override def guessTokenStdInterfaceId(token: TokenId): Future[Option[StdInterfaceId]] = {
          tokenGuesses += 1
          Future.successful(Some(StdInterfaceId.NonStandard))
        }
        override def guessStdInterfaceId(
            address: Address.Contract
        ): Future[Option[StdInterfaceId]] = {
          contractGuesses += 1
          Future.successful(Some(StdInterfaceId.NonStandard))
        }
      }
      (1 to 2).foreach { _ =>
        TokenService.updateTokensMetadata(client).futureValue is ()
        TokenService.updateContractsMetadata(client).futureValue is ()
      }
      tokenGuesses is 1
      contractGuesses is 1
      exec(TokenQueries.listTokenWithoutInterfaceIdQuery()) is ArraySeq.empty
      exec(ContractQueries.listContractWithoutInterfaceIdQuery()) is ArraySeq.empty
    }

    "continue a token batch after failed futures or synchronous exceptions and retry later" in {
      Seq(false, true).foreach { synchronous =>
        val bad  = tokenIdGen.sample.get
        val good = tokenIdGen.sample.get
        addToken(bad)
        addToken(good)
        var unavailable = true
        val client = new EmptyBlockFlowClient {
          override def guessTokenStdInterfaceId(token: TokenId): Future[Option[StdInterfaceId]] = {
            if (token == bad && unavailable) {
              if (synchronous) {
                throw new IllegalStateException("temporary node failure")
              } else {
                Future.failed(new IllegalStateException("temporary node failure"))
              }
            } else {
              Future.successful(Some(StdInterfaceId.NonStandard))
            }
          }
        }
        TokenService.updateTokensMetadata(client).futureValue is ()
        exec(TokenQueries.listTokenWithoutInterfaceIdQuery()) is ArraySeq(bad)
        unavailable = false
        TokenService.updateTokensMetadata(client).futureValue is ()
        exec(TokenQueries.listTokenWithoutInterfaceIdQuery()) is ArraySeq.empty
      }
    }

    "continue a contract batch after a failed item and retry it on the next sync" in {
      val contracts   = addContracts(2)
      val bad         = contracts.head
      var unavailable = true
      val client = new EmptyBlockFlowClient {
        override def guessStdInterfaceId(
            address: Address.Contract
        ): Future[Option[StdInterfaceId]] =
          if (address == bad && unavailable) {
            Future.failed(new IllegalStateException("temporary node failure"))
          } else {
            Future.successful(Some(StdInterfaceId.NonStandard))
          }
      }
      TokenService.updateContractsMetadata(client).futureValue is ()
      exec(ContractQueries.listContractWithoutInterfaceIdQuery()) is ArraySeq(bad)
      unavailable = false
      TokenService.updateContractsMetadata(client).futureValue is ()
      exec(ContractQueries.listContractWithoutInterfaceIdQuery()) is ArraySeq.empty
    }
  }
}
