// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.service

import scala.collection.immutable.ArraySeq
import scala.concurrent.{ExecutionContext, Future}

import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import org.alephium.api.model.{Address => ApiAddress}
import org.alephium.explorer.api.model._
import org.alephium.protocol.model.{Address, TokenId}
import org.alephium.util.U256

trait EmptyTokenService extends TokenService {
  def getTokenBalance(address: ApiAddress, token: TokenId)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[(U256, U256)] = ???

  def listTokens(pagination: Pagination, interfaceIdOpt: Option[StdInterfaceId])(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[TokenInfo]] = ???

  def listTokenTransactions(token: TokenId, pagination: Pagination)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[Transaction]] = ???

  def listTokenInfo(tokens: ArraySeq[TokenId])(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[TokenInfo]] = ???

  def listTokenAddresses(token: TokenId, pagination: Pagination)(implicit
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[Address]] = ???

  def listFungibleTokenMetadata(tokens: ArraySeq[TokenId])(implicit
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[FungibleTokenMetadata]] = ???

  def listNFTMetadata(tokens: ArraySeq[TokenId])(implicit
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[NFTMetadata]] = ???

  def listNFTCollectionMetadata(addresses: ArraySeq[Address.Contract])(implicit
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[NFTCollectionMetadata]] = ???

  def listAddressTokens(address: ApiAddress, pagination: Pagination)(implicit
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[TokenId]] = ???

  def listAddressTokenTransactions(
      address: ApiAddress,
      token: TokenId,
      pagination: Pagination
  )(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[Transaction]] = ???

  def listAddressTokensWithBalance(address: ApiAddress, pagination: Pagination)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[(TokenId, U256, U256)]] = ???

  def listTokenWithoutInterfaceId()(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[TokenId]] = ???

  def listContractWithoutInterfaceId()(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[Address.Contract]] = ???

  def fetchAndStoreTokenMetadata(token: TokenId, blockflowClient: BlockFlowClient)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[Unit] = ???

  def updateTokensMetadata(blockFlowClient: BlockFlowClient)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[Unit] = ???

  def updateContractsMetadata(blockFlowClient: BlockFlowClient)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[Unit] = ???
}
