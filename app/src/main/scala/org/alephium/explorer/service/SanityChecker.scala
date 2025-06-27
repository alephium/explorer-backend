// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.service

import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

import com.typesafe.scalalogging.StrictLogging
import slick.basic.DatabaseConfig
import slick.dbio.DBIOAction
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.{AnyOps, GroupSetting}
import org.alephium.explorer.api.model.BlockEntry
import org.alephium.explorer.cache.BlockCache
import org.alephium.explorer.persistence._
import org.alephium.explorer.persistence.DBRunner._
import org.alephium.explorer.persistence.dao.BlockDao
import org.alephium.explorer.persistence.queries.BlockQueries._
import org.alephium.explorer.persistence.schema.BlockHeaderSchema
import org.alephium.explorer.persistence.schema.CustomJdbcTypes._
import org.alephium.protocol.model.{BlockHash, ChainIndex, GroupIndex}

@SuppressWarnings(Array("org.wartremover.warts.Var", "org.wartremover.warts.IterableOps"))
object SanityChecker extends StrictLogging {

  private def findLatestBlock(
      chainIndex: ChainIndex
  )(implicit dc: DatabaseConfig[PostgresProfile]): Future[Option[BlockHash]] = {
    run(
      BlockHeaderSchema.table
        .filter(header =>
          header.mainChain && header.chainFrom === chainIndex.from && header.chainTo === chainIndex.to
        )
        .sortBy(_.timestamp.desc)
        .map(_.hash)
        .result
        .headOption
    )
  }

  private val running = new AtomicBoolean(false)

  def check()(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      blockFlowClient: BlockFlowClient,
      blockCache: BlockCache,
      groupSetting: GroupSetting
  ): Future[Unit] = {
    if (!running.compareAndSet(false, true)) {
      Future.successful(logger.error("Sanity check already running"))
    } else {
      val blockNum = new AtomicInteger(0)

      val result =
        run(BlockHeaderSchema.table.size.result)
          .flatMap { nbOfBlocks =>
            logger.info(s"Starting sanity check $nbOfBlocks to check")
            Future
              .sequence(groupSetting.chainIndexes.map { chainIndex =>
                findLatestBlock(chainIndex).flatMap {
                  case None => Future.successful(())
                  case Some(hash) =>
                    BlockDao.get(hash).flatMap {
                      case None => Future.successful(())
                      case Some(block) =>
                        checkBlock(block, blockNum, nbOfBlocks)
                    }
                }
              })
          }

      result onComplete { result =>
        result match {
          case Failure(exception) => logger.error("Failed to complete SanityChecker", exception)
          case Success(_)         => logger.info("Sanity Check done")
        }

        running.set(false)

      }

      result.map(_ => ())
    }
  }

  private def checkBlock(block: BlockEntry, blockNum: AtomicInteger, totalNbOfBlocks: Int)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      blockFlowClient: BlockFlowClient,
      blockCache: BlockCache,
      groupSetting: GroupSetting
  ): Future[Unit] = {
    updateMainChain(
      block.hash,
      block.chainFrom,
      block.chainTo,
      groupSetting.groupNum,
      blockNum,
      totalNbOfBlocks
    ).flatMap {
      case None => Future.successful(())
      case Some(missing) =>
        handleMissingBlock(missing, block.chainFrom, blockNum, totalNbOfBlocks)
    }
  }

  @SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
  // scalastyle:off
  private def handleMissingBlock(
      missing: BlockHash,
      chainFrom: GroupIndex,
      blockNum: AtomicInteger,
      totalNbOfBlocks: Int
  )(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      blockFlowClient: BlockFlowClient,
      blockCache: BlockCache,
      groupSetting: GroupSetting
  ): Future[Unit] = {
    // scalastyle:on
    logger.info(s"Downloading missing block $missing")
    blockFlowClient.fetchBlock(chainFrom, missing).flatMap { block =>
      for {
        _ <- BlockDao.insert(block)
        b <- BlockDao.get(block.hash).map(_.get)
        _ <- checkBlock(b, blockNum, totalNbOfBlocks)
      } yield ()
    }
  }

  @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
  private def updateMainChainAction(
      hash: BlockHash,
      chainFrom: GroupIndex,
      chainTo: GroupIndex,
      groupNum: Int,
      blockNum: AtomicInteger,
      totalNbOfBlocks: Int
  )(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): DBActionRWT[Option[BlockHash]] = {
    val nextBlockNum = blockNum.incrementAndGet()
    if (nextBlockNum % 10000 == 0) {
      logger.debug(
        s"Checked $blockNum blocks , progress ${(nextBlockNum.toFloat / totalNbOfBlocks * 100.0).toInt}%"
      )
    }
    getBlockEntryAction(hash)
      .flatMap {
        case Some(block) if !block.mainChain =>
          logger.debug(s"Updating block ${block.hash} which should be on the mainChain")
          assert(block.chainFrom == chainFrom && block.chainTo == chainTo)
          for {
            blocks <- getAtHeightAction(block.chainFrom, block.chainTo, block.height)
            _ <- DBIOAction.sequence(
              blocks
                .map(_.hash)
                .filterNot(_ === block.hash)
                .map(updateMainChainStatusQuery(_, false))
            )
            _ <- updateMainChainStatusQuery(hash, true)
          } yield {
            block.parent(groupNum).map(Right(_))
          }
        case None        => DBIOAction.successful(Some(Left(hash)))
        case Some(block) => DBIOAction.successful(block.parent(groupNum).map(Right(_)))
      }
      .flatMap {
        case Some(Right(parent)) =>
          updateMainChainAction(parent, chainFrom, chainTo, groupNum, blockNum, totalNbOfBlocks)
        case Some(Left(missing)) => DBIOAction.successful(Some(missing))
        case None => DBIOAction.successful(None) // No parent, we reach the genesis blocks
      }
  }

  def updateMainChain(
      hash: BlockHash,
      chainFrom: GroupIndex,
      chainTo: GroupIndex,
      groupNum: Int,
      blockNum: AtomicInteger,
      totalNbOfBlocks: Int
  )(implicit ec: ExecutionContext, dc: DatabaseConfig[PostgresProfile]): Future[Option[BlockHash]] =
    run(updateMainChainAction(hash, chainFrom, chainTo, groupNum, blockNum, totalNbOfBlocks))
}
