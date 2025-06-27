// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

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

  def getBlockByHash(hash: BlockHash)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[Option[BlockEntry]] = Future.successful(None)

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
