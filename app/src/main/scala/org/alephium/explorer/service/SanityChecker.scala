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

import scala.concurrent.{ExecutionContext, Future}

import com.typesafe.scalalogging.StrictLogging
import slick.basic.DatabaseConfig
import slick.dbio.DBIOAction
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.{AnyOps, GroupSetting}
import org.alephium.explorer.api.model.{BlockEntry, GroupIndex}
import org.alephium.explorer.cache.BlockCache
import org.alephium.explorer.persistence._
import org.alephium.explorer.persistence.DBRunner._
import org.alephium.explorer.persistence.dao.BlockDao
import org.alephium.explorer.persistence.queries.BlockQueries._
import org.alephium.explorer.persistence.schema.BlockHeaderSchema
import org.alephium.explorer.persistence.schema.CustomJdbcTypes._
import org.alephium.protocol.model.BlockHash

@SuppressWarnings(Array("org.wartremover.warts.Var", "org.wartremover.warts.IterableOps"))
object SanityChecker extends StrictLogging {

  private def findLatestBlock(from: GroupIndex, to: GroupIndex)(
      implicit dc: DatabaseConfig[PostgresProfile]): Future[Option[BlockHash]] = {
    run(
      BlockHeaderSchema.table
        .filter(header => header.mainChain && header.chainFrom === from && header.chainTo === to)
        .sortBy(_.timestamp.desc)
        .map(_.hash)
        .result
        .headOption
    )
  }

  private var running: Boolean = false
  private var i                = 0
  private var totalNbOfBlocks  = 0

  def check()(implicit ec: ExecutionContext,
              dc: DatabaseConfig[PostgresProfile],
              blockFlowClient: BlockFlowClient,
              blockCache: BlockCache,
              groupSetting: GroupSetting): Future[Unit] = {
    if (running) {
      Future.successful(logger.error("Sanity check already running"))
    } else {
      running = true
      i       = 0
      run(BlockHeaderSchema.table.size.result).flatMap { nbOfBlocks =>
        totalNbOfBlocks = nbOfBlocks
        logger.info(s"Starting sanity check $totalNbOfBlocks to check")
        Future
          .sequence(groupSetting.groupIndexes.map {
            case (from, to) =>
              findLatestBlock(from, to).flatMap {
                case None => Future.successful(())
                case Some(hash) =>
                  BlockDao.get(hash).flatMap {
                    case None => Future.successful(())
                    case Some(block) =>
                      checkBlock(block)
                  }
              }
          })
          .map { _ =>
            running = false
            logger.info(s"Sanity Check done")
          }
      }
    }
  }

  private def checkBlock(block: BlockEntry)(implicit ec: ExecutionContext,
                                            dc: DatabaseConfig[PostgresProfile],
                                            blockFlowClient: BlockFlowClient,
                                            blockCache: BlockCache,
                                            groupSetting: GroupSetting): Future[Unit] = {
    updateMainChain(block.hash, block.chainFrom, block.chainTo, groupSetting.groupNum).flatMap {
      case None          => Future.successful(())
      case Some(missing) => handleMissingBlock(missing, block.chainFrom)
    }
  }

  @SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
  private def handleMissingBlock(missing: BlockHash, chainFrom: GroupIndex)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      blockFlowClient: BlockFlowClient,
      blockCache: BlockCache,
      groupSetting: GroupSetting): Future[Unit] = {
    logger.info(s"Downloading missing block $missing")
    blockFlowClient.fetchBlock(chainFrom, missing).flatMap { block =>
      for {
        _ <- BlockDao.insert(block)
        b <- BlockDao.get(block.hash).map(_.get)
        _ <- checkBlock(b)
      } yield ()
    }
  }

  @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
  private def updateMainChainAction(hash: BlockHash,
                                    chainFrom: GroupIndex,
                                    chainTo: GroupIndex,
                                    groupNum: Int)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): DBActionRWT[Option[BlockHash]] = {
    i = i + 1
    if (i % 10000 == 0) {
      logger.debug(s"Checked $i blocks , progress ${(i.toFloat / totalNbOfBlocks * 100.0).toInt}%")
    }
    getBlockEntryWithoutTxsAction(hash)
      .flatMap {
        case Some(block) if !block.mainChain =>
          logger.debug(s"Updating block ${block.hash} which should be on the mainChain")
          assert(block.chainFrom == chainFrom && block.chainTo == chainTo)
          (for {
            blocks <- getAtHeightAction(block.chainFrom, block.chainTo, block.height)
            _ <- DBIOAction.sequence(
              blocks
                .map(_.hash)
                .filterNot(_ === block.hash)
                .map(updateMainChainStatusAction(_, false)))
            _ <- updateMainChainStatusAction(hash, true)
          } yield {
            block.parent(groupNum).map(Right(_))
          })
        case None        => DBIOAction.successful(Some(Left(hash)))
        case Some(block) => DBIOAction.successful(block.parent(groupNum).map(Right(_)))
      }
      .flatMap {
        case Some(Right(parent)) => updateMainChainAction(parent, chainFrom, chainTo, groupNum)
        case Some(Left(missing)) => DBIOAction.successful(Some(missing))
        case None                => DBIOAction.successful(None) //No parent, we reach the genesis blocks
      }
  }

  def updateMainChain(hash: BlockHash, chainFrom: GroupIndex, chainTo: GroupIndex, groupNum: Int)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]): Future[Option[BlockHash]] =
    run(updateMainChainAction(hash, chainFrom, chainTo, groupNum))
}
