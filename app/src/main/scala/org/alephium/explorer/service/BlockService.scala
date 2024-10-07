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

import org.alephium.explorer.GroupSetting
import org.alephium.explorer.api.model._
import org.alephium.explorer.cache.BlockCache
import org.alephium.explorer.persistence.dao.BlockDao
import org.alephium.protocol.model.BlockHash

trait BlockService {
  def getBlockByHash(hash: BlockHash)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[Option[BlockEntry]]

  def getLiteBlockByHash(hash: BlockHash)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[Option[BlockEntryLite]]

  def getBlockTransactions(hash: BlockHash, pagination: Pagination)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[Transaction]]

  def listBlocks(pagination: Pagination.Reversible)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      cache: BlockCache
  ): Future[ListBlocks]

  def listMaxHeights()(implicit
      cache: BlockCache,
      groupSetting: GroupSetting,
      ec: ExecutionContext
  ): Future[ArraySeq[PerChainHeight]]

  def getAverageBlockTime()(implicit
      cache: BlockCache,
      groupSetting: GroupSetting,
      ec: ExecutionContext
  ): Future[ArraySeq[PerChainDuration]]
}

object BlockService extends BlockService {
  def getBlockByHash(hash: BlockHash)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[Option[BlockEntry]] =
    BlockDao.get(hash)

  def getLiteBlockByHash(hash: BlockHash)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[Option[BlockEntryLite]] =
    BlockDao.getLite(hash)

  def getBlockTransactions(hash: BlockHash, pagination: Pagination)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[Transaction]] =
    BlockDao.getTransactions(hash, pagination)

  def listBlocks(pagination: Pagination.Reversible)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      cache: BlockCache
  ): Future[ListBlocks] =
    BlockDao.listMainChain(pagination).map { case (blocks, total) =>
      ListBlocks(total, blocks)
    }

  def listMaxHeights()(implicit
      cache: BlockCache,
      groupSetting: GroupSetting,
      ec: ExecutionContext
  ): Future[ArraySeq[PerChainHeight]] =
    BlockDao
      .latestBlocks()
      .map(_.map { case (chainIndex, block) =>
        val height = block.height.value.toLong
        PerChainHeight(chainIndex.from.value, chainIndex.to.value, height, height)
      })

  def getAverageBlockTime()(implicit
      cache: BlockCache,
      groupSetting: GroupSetting,
      ec: ExecutionContext
  ): Future[ArraySeq[PerChainDuration]] =
    BlockDao
      .getAverageBlockTime()
      .map(_.map { case (chainIndex, averageBlockTime) =>
        val duration = averageBlockTime.millis
        PerChainDuration(chainIndex.from.value, chainIndex.to.value, duration, duration)
      })
}
