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

import org.alephium.explorer._
import org.alephium.explorer.api.model._
import org.alephium.explorer.cache.BlockCache
import org.alephium.protocol.model.BlockHash

trait EmptyBlockService extends BlockService {

  def getLiteBlockByHash(hash: BlockHash)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[Option[BlockEntryLite]] =
    Future.successful(None)

  def getBlockTransactions(hash: BlockHash, pagination: Pagination)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[Transaction]] =
    Future.successful(ArraySeq.empty)

  def listBlocks(pagination: Pagination.Reversible)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      cache: BlockCache
  ): Future[ListBlocks] =
    Future.successful(ListBlocks(0, ArraySeq.empty))

  def listMaxHeights()(implicit
      cache: BlockCache,
      groupSetting: GroupSetting,
      ec: ExecutionContext
  ): Future[ArraySeq[PerChainHeight]] =
    Future.successful(ArraySeq.empty)

  def getAverageBlockTime()(implicit
      cache: BlockCache,
      groupSetting: GroupSetting,
      ec: ExecutionContext
  ): Future[ArraySeq[PerChainDuration]] =
    Future.successful(ArraySeq.empty)

}
