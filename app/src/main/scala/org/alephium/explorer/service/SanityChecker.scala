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
import slick.jdbc.JdbcProfile

import org.alephium.explorer.api.model.{BlockEntry, GroupIndex}
import org.alephium.explorer.persistence._
import org.alephium.explorer.persistence.dao.BlockDao
import org.alephium.explorer.persistence.queries.BlockQueries

@SuppressWarnings(Array("org.wartremover.warts.Var", "org.wartremover.warts.TraversableOps"))
class SanityChecker(
    groupNum: Int,
    blockFlowClient: BlockFlowClient,
    blockDao: BlockDao,
    val config: DatabaseConfig[JdbcProfile])(implicit val executionContext: ExecutionContext)
    extends BlockQueries
    with DBRunner
    with StrictLogging {
  import config.profile.api._

  private def findLatestBlock(from: GroupIndex, to: GroupIndex): Future[Option[BlockEntry.Hash]] = {
    run(
      blockHeadersTable
        .filter(header => header.mainChain && header.chainFrom === from && header.chainTo === to)
        .sortBy(_.timestamp.desc)
        .map(_.hash)
        .result
        .headOption
    )
  }

  private val chainIndexes: Seq[(GroupIndex, GroupIndex)] = for {
    i <- 0 to groupNum - 1
    j <- 0 to groupNum - 1
  } yield (GroupIndex.unsafe(i), GroupIndex.unsafe(j))

  private var running: Boolean = false
  private var i                = 0
  private var totalNbOfBlocks  = 0

  def check(): Future[Unit] = {
    if (running) {
      Future.successful(logger.error("Sanity check already running"))
    } else {
      running = true
      i       = 0
      run(blockHeadersTable.size.result).flatMap { nbOfBlocks =>
        totalNbOfBlocks = nbOfBlocks
        logger.info(s"Starting sanity check $totalNbOfBlocks to check")
        Future
          .sequence(chainIndexes.map {
            case (from, to) =>
              findLatestBlock(from, to).flatMap {
                case None => Future.successful(())
                case Some(hash) =>
                  blockDao.get(hash).flatMap {
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

  private def checkBlock(block: BlockEntry): Future[Unit] = {
    updateMainChain(block.hash, block.chainFrom, block.chainTo, groupNum).flatMap {
      case None          => Future.successful(())
      case Some(missing) => handleMissingBlock(missing, block.chainFrom)
    }
  }

  @SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
  private def handleMissingBlock(missing: BlockEntry.Hash, chainFrom: GroupIndex): Future[Unit] = {
    logger.info(s"Downloading missing block $missing")
    blockFlowClient.fetchBlock(chainFrom, missing).flatMap {
      case Left(error) => Future.successful(logger.error(error))
      case Right(block) =>
        for {
          _ <- blockDao.insert(block)
          b <- blockDao.get(block.hash).map(_.get)
          _ <- checkBlock(b)
        } yield ()
    }
  }

  @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
  private def updateMainChainAction(hash: BlockEntry.Hash,
                                    chainFrom: GroupIndex,
                                    chainTo: GroupIndex,
                                    groupNum: Int): DBActionRWT[Option[BlockEntry.Hash]] = {
    i = i + 1
    if (i % 10000 == 0) {
      logger.debug(s"Checked $i blocks , progress ${(i.toFloat / totalNbOfBlocks * 100.0).toInt}%")
    }
    getBlockEntryWithoutTxsAction(hash)
      .flatMap {
        case Some(block)  =>
          assert(block.chainFrom == chainFrom && block.chainTo == chainTo)
          (for {
            blocks <- getAtHeightAction(block.chainFrom, block.chainTo, block.height)
          } yield {
            if(blocks.map(_.mainChain).filter(_ == true).size > 1){
              logger.error(s"ERRRRRRRRROR block ${block}")
            }
            block.parent(groupNum).map(Right(_))
          })
        case None        => DBIOAction.successful(Some(Left(hash)))
      }
      .flatMap {
        case Some(Right(parent)) => updateMainChainAction(parent, chainFrom, chainTo, groupNum)
        case Some(Left(missing)) => DBIOAction.successful(Some(missing))
        case None                => DBIOAction.successful(None) //No parent, we reach the genesis blocks
      }
  }

  def updateMainChain(hash: BlockEntry.Hash,
                      chainFrom: GroupIndex,
                      chainTo: GroupIndex,
                      groupNum: Int): Future[Option[BlockEntry.Hash]] = {
    run(updateMainChainAction(hash, chainFrom, chainTo, groupNum))
  }
}
