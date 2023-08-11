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

import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import org.alephium.explorer.api.model._
import org.alephium.protocol.model.{Address, TokenId}
import org.alephium.util.U256

trait EmptyTokenService extends TokenService {
  def getTokenBalance(address: Address, token: TokenId)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[(U256, U256)] = ???

  def listTokens(pagination: Pagination)(implicit
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[TokenInfo]] = ???

  def listTokenTransactions(token: TokenId, pagination: Pagination)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[Transaction]] = ???

  def listTokenInfo(tokens: ArraySeq[TokenId])(implicit
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

  def listAddressTokens(address: Address, pagination: Pagination)(implicit
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[TokenId]] = ???

  def listAddressTokenTransactions(address: Address, token: TokenId, pagination: Pagination)(
      implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[Transaction]] = ???

  def listAddressTokensWithBalance(address: Address, pagination: Pagination)(implicit
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
