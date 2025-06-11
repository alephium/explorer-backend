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

import sttp.model.Uri

import org.alephium.api.model.{ChainInfo, ChainParams, HashesAtHeight, SelfClique}
import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence.model._
import org.alephium.protocol.model.{Address, BlockHash, ChainIndex, GroupIndex, TokenId}
import org.alephium.util.{Service, TimeStamp}

trait EmptyBlockFlowClient extends BlockFlowClient {
  implicit val executionContext: ExecutionContext = implicitly
  override def startSelfOnce(): Future[Unit]      = Future.unit
  override def stopSelfOnce(): Future[Unit]       = Future.unit
  override def subServices: ArraySeq[Service]     = ArraySeq.empty
  override def fetchBlock(fromGroup: GroupIndex, hash: BlockHash): Future[BlockEntityWithEvents] =
    ???

  override def fetchChainInfo(chainIndex: ChainIndex): Future[ChainInfo] = ???

  override def fetchHashesAtHeight(
      chainIndex: ChainIndex,
      height: Height
  ): Future[HashesAtHeight] = ???

  override def fetchBlockAndEvents(
      fromGroup: GroupIndex,
      hash: BlockHash
  ): Future[BlockEntityWithEvents] = ???

  override def fetchBlocks(
      fromTs: TimeStamp,
      toTs: TimeStamp,
      uri: Uri
  ): Future[ArraySeq[ArraySeq[BlockEntityWithEvents]]] = ???

  override def fetchSelfClique(): Future[SelfClique] = ???

  override def fetchChainParams(): Future[ChainParams] = ???

  override def fetchMempoolTransactions(uri: Uri): Future[ArraySeq[MempoolTransaction]] = ???

  override def guessStdInterfaceId(address: Address.Contract): Future[Option[StdInterfaceId]] =
    Future.successful(None)

  override def guessTokenStdInterfaceId(token: TokenId): Future[Option[StdInterfaceId]] =
    Future.successful(None)

  override def fetchFungibleTokenMetadata(token: TokenId): Future[Option[FungibleTokenMetadata]] =
    Future.successful(None)

  override def fetchNFTMetadata(token: TokenId): Future[Option[NFTMetadata]] =
    Future.successful(None)

  override def fetchNFTCollectionMetadata(
      contract: Address.Contract
  ): Future[Option[NFTCollectionMetadata]] =
    Future.successful(None)

  override def start(): Future[Unit] = ???

  override def close(): Future[Unit] = ???
}
