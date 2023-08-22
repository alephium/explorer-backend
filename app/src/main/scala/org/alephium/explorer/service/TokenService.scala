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

package org.alephium.explorer.service

import scala.collection.immutable.ArraySeq
import scala.concurrent.{ExecutionContext, Future}

import com.typesafe.scalalogging.StrictLogging
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence.DBRunner._
import org.alephium.explorer.persistence.queries.ContractQueries._
import org.alephium.explorer.persistence.queries.TokenQueries._
import org.alephium.protocol.model.{Address, TokenId}
import org.alephium.util.U256

trait TokenService {
  def getTokenBalance(address: Address, token: TokenId)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[(U256, U256)]

  def listTokens(pagination: Pagination, interfaceIdOpt: Option[StdInterfaceId])(implicit
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[TokenInfo]]

  def listTokenTransactions(token: TokenId, pagination: Pagination)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[Transaction]]

  def listTokenInfo(tokens: ArraySeq[TokenId])(implicit
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[TokenInfo]]

  def listTokenAddresses(token: TokenId, pagination: Pagination)(implicit
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[Address]]

  def listFungibleTokenMetadata(tokens: ArraySeq[TokenId])(implicit
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[FungibleTokenMetadata]]

  def listNFTMetadata(tokens: ArraySeq[TokenId])(implicit
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[NFTMetadata]]

  def listNFTCollectionMetadata(addresses: ArraySeq[Address.Contract])(implicit
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[NFTCollectionMetadata]]

  def listAddressTokens(address: Address, pagination: Pagination)(implicit
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[TokenId]]

  def listAddressTokenTransactions(address: Address, token: TokenId, pagination: Pagination)(
      implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[Transaction]]

  def listAddressTokensWithBalance(address: Address, pagination: Pagination)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[(TokenId, U256, U256)]]

  def listTokenWithoutInterfaceId()(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[TokenId]]

  def listContractWithoutInterfaceId()(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[Address.Contract]]

  def fetchAndStoreTokenMetadata(token: TokenId, blockflowClient: BlockFlowClient)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[Unit]

  def updateTokensMetadata(blockFlowClient: BlockFlowClient)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[Unit]

  def updateContractsMetadata(blockFlowClient: BlockFlowClient)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[Unit]
}

object TokenService extends TokenService with StrictLogging {

  def getTokenBalance(address: Address, token: TokenId)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[(U256, U256)] =
    run(getTokenBalanceAction(address, token))

  def listTokens(pagination: Pagination, interfaceIdOpt: Option[StdInterfaceId])(implicit
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[TokenInfo]] =
    run(listTokensAction(pagination, interfaceIdOpt))

  def listTokenTransactions(token: TokenId, pagination: Pagination)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[Transaction]] =
    run(getTransactionsByToken(token, pagination))

  def listTokenAddresses(token: TokenId, pagination: Pagination)(implicit
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[Address]] =
    run(getAddressesByToken(token, pagination))

  def listTokenInfo(tokens: ArraySeq[TokenId])(implicit
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[TokenInfo]] =
    run(listTokenInfosQuery(tokens))

  def listFungibleTokenMetadata(tokens: ArraySeq[TokenId])(implicit
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[FungibleTokenMetadata]] =
    run(listFungibleTokenMetadataQuery(tokens))

  def listNFTMetadata(tokens: ArraySeq[TokenId])(implicit
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[NFTMetadata]] =
    run(listNFTMetadataQuery(tokens))

  def listNFTCollectionMetadata(addresses: ArraySeq[Address.Contract])(implicit
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[NFTCollectionMetadata]] =
    run(listNFTCollectionMetadataQuery(addresses))

  def listAddressTokens(address: Address, pagination: Pagination)(implicit
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[TokenId]] =
    run(listAddressTokensAction(address, pagination))

  def listAddressTokenTransactions(address: Address, token: TokenId, pagination: Pagination)(
      implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[Transaction]] =
    run(getTokenTransactionsByAddress(address, token, pagination))

  def listAddressTokensWithBalance(address: Address, pagination: Pagination)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[(TokenId, U256, U256)]] =
    run(listAddressTokensWithBalanceAction(address, pagination))

  def listTokenWithoutInterfaceId()(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[TokenId]] = {
    run(listTokenWithoutInterfaceIdQuery())
  }

  def listContractWithoutInterfaceId()(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[Address.Contract]] = {
    run(listContractWithoutInterfaceIdQuery())
  }

  // Interface Id is store after the metadata is stored in case it fail in the middle of the
  // metadata insert. As we rely on the empyt interface id to know which token to update.
  def fetchAndStoreTokenMetadata(token: TokenId, blockflowClient: BlockFlowClient)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[Unit] = {
    logger.debug(s"Update token $token")
    blockflowClient.guessTokenStdInterfaceId(token).flatMap { interfaceIdOpt =>
      interfaceIdOpt match {
        case Some(StdInterfaceId.FungibleToken) =>
          blockflowClient.fetchFungibleTokenMetadata(token).flatMap {
            case Some(metadata) =>
              run((for {
                _ <- insertFungibleTokenMetadata(metadata)
                _ <- updateTokenInterfaceId(token, StdInterfaceId.FungibleToken)
              } yield ()).transactionally)
            case None =>
              Future.unit
          }
        case Some(StdInterfaceId.NFT) =>
          blockflowClient.fetchNFTMetadata(token).flatMap {
            case Some(metadata) =>
              run((for {
                _ <- insertNFTMetadata(metadata)
                _ <- updateTokenInterfaceId(token, StdInterfaceId.NFT)
              } yield ()).transactionally)
            case None =>
              Future.unit
          }
        // NFT collection aren't token
        case Some(StdInterfaceId.NFTCollection) =>
          Future.unit
        case Some(StdInterfaceId.NonStandard) =>
          Future.unit
        case None =>
          Future.unit
      }
    }
  }

  def fetchAndStoreContractMetadata(contract: Address.Contract, blockflowClient: BlockFlowClient)(
      implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[Unit] = {
    logger.debug(s"Update contract $contract")
    blockflowClient.guessStdInterfaceId(contract).flatMap { interfaceIdOpt =>
      interfaceIdOpt match {
        case Some(StdInterfaceId.NFTCollection) =>
          blockflowClient.fetchNFTCollectionMetadata(contract).flatMap {
            case Some(metadata) =>
              run((for {
                _ <- insertNFTCollectionMetadata(contract, metadata)
                _ <- updateContractInterfaceId(contract, StdInterfaceId.NFTCollection)
              } yield ()).transactionally)
            case None => Future.unit
          }
        case Some(StdInterfaceId.FungibleToken) =>
          Future.unit
        case Some(StdInterfaceId.NFT) =>
          Future.unit
        case Some(StdInterfaceId.NonStandard) =>
          Future.unit
        case None =>
          Future.unit
      }
    }
  }

  def updateTokensMetadata(blockFlowClient: BlockFlowClient)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[Unit] = {
    listTokenWithoutInterfaceId()
      .flatMap { tokens =>
        Future.sequence(tokens.map(token => fetchAndStoreTokenMetadata(token, blockFlowClient)))
      }
      .map(_ => ())
  }

  def updateContractsMetadata(blockFlowClient: BlockFlowClient)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[Unit] = {
    listContractWithoutInterfaceId()
      .flatMap { tokens =>
        Future.sequence(
          tokens.map(contract => fetchAndStoreContractMetadata(contract, blockFlowClient))
        )
      }
      .map(_ => ())
  }
}
