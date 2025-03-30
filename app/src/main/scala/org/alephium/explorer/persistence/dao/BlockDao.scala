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

import scala.collection.immutable.ArraySeq
import scala.concurrent.{ExecutionContext, Future}

import slick.basic.DatabaseConfig
import slick.dbio.DBIOAction
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.GroupSetting
import org.alephium.explorer.api.model._
import org.alephium.explorer.cache.BlockCache
import org.alephium.explorer.persistence._
import org.alephium.explorer.persistence.DBRunner._
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.persistence.queries.BlockQueries._
import org.alephium.explorer.persistence.queries.ContractQueries._
import org.alephium.explorer.persistence.queries.EventQueries._
import org.alephium.explorer.persistence.queries.TransactionQueries._
import org.alephium.explorer.persistence.schema._
import org.alephium.explorer.persistence.schema.CustomGetResult._
import org.alephium.explorer.persistence.schema.CustomSetParameter._
import org.alephium.explorer.util.SlickUtil._
import org.alephium.protocol.model.{BlockHash, ChainIndex, GroupIndex}
import org.alephium.util.{Duration, TimeStamp}

object BlockDao {

  def getLite(hash: BlockHash)(implicit
      dc: DatabaseConfig[PostgresProfile]
  ): Future[Option[BlockEntryLite]] =
    run(getBlockEntryLiteAction(hash))

  def getTransactions(hash: BlockHash, pagination: Pagination)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[Transaction]] =
    run(getTransactionsByBlockHashWithPagination(hash, pagination))

  def get(hash: BlockHash)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[Option[BlockEntry]] =
    run(getBlockEntryAction(hash))

  def getAtHeight(fromGroup: GroupIndex, toGroup: GroupIndex, height: Height)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[BlockEntry]] =
    run(getAtHeightAction(fromGroup, toGroup, height))

  /** Inserts a single block transactionally via SQL */
  def insert(block: BlockEntity)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      groupSetting: GroupSetting
  ): Future[Unit] =
    insertAll(ArraySeq(block))

  def insertWithEvents(block: BlockEntity, events: ArraySeq[EventEntity])(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      groupSetting: GroupSetting
  ): Future[Unit] =
    run((for {
      _ <- insertBlockEntity(ArraySeq(block), groupSetting.groupNum)
      _ <- insertEventsQuery(events)
      _ <- insertOrUpdateContracts(events, block.chainFrom)
    } yield ()).transactionally)

  /** Inserts a multiple blocks transactionally via SQL */
  def insertAll(blocks: ArraySeq[BlockEntity])(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      groupSetting: GroupSetting
  ): Future[Unit] =
    run(insertBlockEntity(blocks, groupSetting.groupNum)).map(_ => ())

  def listMainChain(pagination: Pagination.Reversible)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      cache: BlockCache
  ): Future[(ArraySeq[BlockEntryLite], Int)] = {
    run(listMainChainHeadersWithTxnNumber(pagination)).map { blockEntries =>
      (blockEntries, cache.getMainChainBlockCount())
    }
  }

  def listIncludingForks(from: TimeStamp, to: TimeStamp)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[ArraySeq[BlockEntryLite]] = {
    run(sql"""
      SELECT *
      FROM block_headers
      WHERE block_timestamp >= $from
      AND block_timestamp <= $to
      ORDER BY block_timestamp DESC, hash

      """.asASE[BlockHeader](blockHeaderGetResult)).map(_.map(_.toLiteApi))
  }

  @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
  private def updateMainChainAction(
      hash: BlockHash,
      chainFrom: GroupIndex,
      chainTo: GroupIndex,
      groupNum: Int
  )(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): DBActionRWT[Option[BlockHash]] =
    getBlockHeaderAction(hash)
      .flatMap {
        case Some(block) if !block.mainChain =>
          assert(block.chainFrom == chainFrom && block.chainTo == chainTo)
          for {
            blocks <- getHashesAtHeightIgnoringOne(
              fromGroup = block.chainFrom,
              toGroup = block.chainTo,
              height = block.height,
              hashToIgnore = block.hash
            )
            _ <- DBIOAction.sequence(
              blocks
                .map(updateMainChainStatusQuery(_, false))
            )
            _ <- updateMainChainStatusQuery(hash, true)
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

  def updateMainChain(hash: BlockHash, chainFrom: GroupIndex, chainTo: GroupIndex, groupNum: Int)(
      implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[Option[BlockHash]] =
    run(updateMainChainAction(hash, chainFrom, chainTo, groupNum))

  def updateMainChainStatus(hash: BlockHash, isMainChain: Boolean)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[Unit] =
    run(updateMainChainStatusQuery(hash, isMainChain).map(_ => ()))

  def latestBlocks()(implicit
      cache: BlockCache,
      groupSetting: GroupSetting,
      ec: ExecutionContext
  ): Future[ArraySeq[(ChainIndex, LatestBlock)]] =
    cache.getAllLatestBlocks()

  def getAverageBlockTime()(implicit
      cache: BlockCache,
      groupSetting: GroupSetting,
      ec: ExecutionContext
  ): Future[ArraySeq[(ChainIndex, Duration)]] =
    cache.getAllBlockTimes(groupSetting.chainIndexes)

  def updateLatestBlock(block: BlockEntity)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      cache: BlockCache,
      groupSetting: GroupSetting
  ): Future[Unit] = {
    val chainIndex =
      ChainIndex.unsafe(block.chainFrom.value, block.chainTo.value)(groupSetting.groupConfig)
    val latestBlock = LatestBlock.fromEntity(block)
    run(LatestBlockSchema.table.insertOrUpdate(latestBlock)).map { _ =>
      cache.putLatestBlock(chainIndex, latestBlock)
    }
  }
}
