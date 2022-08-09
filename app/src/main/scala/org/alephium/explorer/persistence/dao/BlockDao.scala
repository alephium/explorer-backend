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

package org.alephium.explorer.persistence.dao

import scala.concurrent.{ExecutionContext, Future}

import slick.basic.DatabaseConfig
import slick.dbio.DBIOAction
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.{AnyOps, GroupSetting}
import org.alephium.explorer.api.model._
import org.alephium.explorer.cache.BlockCache
import org.alephium.explorer.persistence._
import org.alephium.explorer.persistence.DBRunner._
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.persistence.queries.BlockQueries._
import org.alephium.explorer.persistence.queries.TransactionQueries._
import org.alephium.explorer.persistence.schema._
import org.alephium.explorer.persistence.schema.CustomJdbcTypes._
import org.alephium.protocol.model.ChainIndex
import org.alephium.util.{Duration, TimeStamp}

object BlockDao {

  def getLite(hash: BlockEntry.Hash)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[Option[BlockEntryLite]] =
    run(getBlockEntryLiteAction(hash))

  def getTransactions(hash: BlockEntry.Hash, pagination: Pagination)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[Seq[Transaction]] =
    run(getTransactionsByBlockHashWithPagination(hash, pagination))

  def get(hash: BlockEntry.Hash)(implicit ec: ExecutionContext,
                                 dc: DatabaseConfig[PostgresProfile]): Future[Option[BlockEntry]] =
    run(getBlockEntryAction(hash))

  def getAtHeight(fromGroup: GroupIndex, toGroup: GroupIndex, height: Height)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[Seq[BlockEntry]] =
    run(getAtHeightAction(fromGroup, toGroup, height))

  /** Inserts a single block transactionally via SQL */
  def insert(block: BlockEntity)(implicit ec: ExecutionContext,
                                 dc: DatabaseConfig[PostgresProfile],
                                 groupSetting: GroupSetting): Future[Unit] =
    insertAll(Seq(block))

  /** Inserts a multiple blocks transactionally via SQL */
  def insertAll(blocks: Seq[BlockEntity])(implicit ec: ExecutionContext,
                                          dc: DatabaseConfig[PostgresProfile],
                                          groupSetting: GroupSetting): Future[Unit] =
    run(insertBlockEntity(blocks, groupSetting.groupNum)).map(_ => ())

  def listMainChain(pagination: Pagination)(
      implicit ec: ExecutionContext,
      cache: BlockCache): Future[(Seq[BlockEntryLite], Int)] = {
    cache.listMainChainHeaders(pagination).map { blockEntries =>
      (blockEntries, cache.getMainChainBlockCount())
    }
  }

  def listIncludingForks(from: TimeStamp, to: TimeStamp)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[Seq[BlockEntryLite]] = {
    val action =
      for {
        headers <- BlockHeaderSchema.table
          .filter(header => header.timestamp >= from && header.timestamp <= to)
          .sortBy(b => (b.timestamp.desc, b.hash))
          .result
      } yield headers.map(_.toLiteApi)

    run(action)
  }

  def maxHeight(fromGroup: GroupIndex, toGroup: GroupIndex)(
      implicit dc: DatabaseConfig[PostgresProfile]): Future[Option[Height]] = {
    val query =
      BlockHeaderSchema.table
        .filter(header => header.chainFrom === fromGroup && header.chainTo === toGroup)
        .map(_.height)
        .max

    run(query.result)
  }

  @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
  private def updateMainChainAction(hash: BlockEntry.Hash,
                                    chainFrom: GroupIndex,
                                    chainTo: GroupIndex,
                                    groupNum: Int)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): DBActionRWT[Option[BlockEntry.Hash]] =
    getBlockHeaderAction(hash)
      .flatMap {
        case Some(block) if !block.mainChain =>
          assert(block.chainFrom == chainFrom && block.chainTo == chainTo)
          for {
            blocks <- getAtHeightAction(block.chainFrom, block.chainTo, block.height)
            _ <- DBIOAction.sequence(
              blocks
                .map(_.hash)
                .filterNot(_ === block.hash)
                .map(updateMainChainStatusAction(_, false)))
            _ <- updateMainChainStatusAction(hash, true)
          } yield {
            block.parent.map(Right(_))
          }
        case None => DBIOAction.successful(Some(Left(hash)))
        case _    => DBIOAction.successful(None)
      }
      .flatMap {
        case Some(Right(parent)) => updateMainChainAction(parent, chainFrom, chainTo, groupNum)
        case Some(Left(missing)) => DBIOAction.successful(Some(missing))
        case None                => DBIOAction.successful(None)
      }

  def updateMainChain(hash: BlockEntry.Hash,
                      chainFrom: GroupIndex,
                      chainTo: GroupIndex,
                      groupNum: Int)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[Option[BlockEntry.Hash]] =
    run(updateMainChainAction(hash, chainFrom, chainTo, groupNum))

  def updateMainChainStatus(hash: BlockEntry.Hash, isMainChain: Boolean)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[Unit] =
    run(updateMainChainStatusAction(hash, isMainChain))

  def latestBlocks()(implicit cache: BlockCache,
                     groupSetting: GroupSetting,
                     ec: ExecutionContext): Future[Seq[(ChainIndex, LatestBlock)]] =
    cache.getAllLatestBlocks()

  def getAverageBlockTime()(implicit cache: BlockCache,
                            groupSetting: GroupSetting,
                            ec: ExecutionContext): Future[Seq[(ChainIndex, Duration)]] =
    cache.getAllBlockTimes(groupSetting.chainIndexes)

  def updateLatestBlock(block: BlockEntity)(implicit ec: ExecutionContext,
                                            dc: DatabaseConfig[PostgresProfile],
                                            cache: BlockCache,
                                            groupSetting: GroupSetting): Future[Unit] = {
    val chainIndex =
      ChainIndex.unsafe(block.chainFrom.value, block.chainTo.value)(groupSetting.groupConfig)
    val latestBlock = LatestBlock.fromEntity(block)
    run(LatestBlockSchema.table.insertOrUpdate(latestBlock)).map { _ =>
      cache.putLatestBlock(chainIndex, latestBlock)
    }
  }
}
