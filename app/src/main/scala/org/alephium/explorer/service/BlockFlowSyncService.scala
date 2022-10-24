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

import java.util.concurrent.atomic.AtomicBoolean

import scala.collection.immutable.ArraySeq
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.{Duration => ScalaDuration, FiniteDuration}

import com.typesafe.scalalogging.StrictLogging
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile
import sttp.model.Uri

import org.alephium.explorer.{foldFutures, GroupSetting}
import org.alephium.explorer.api.model.{GroupIndex, Height}
import org.alephium.explorer.cache.BlockCache
import org.alephium.explorer.error.ExplorerError.BlocksInDifferentChains
import org.alephium.explorer.persistence.DBRunner._
import org.alephium.explorer.persistence.dao.BlockDao
import org.alephium.explorer.persistence.model.BlockEntity
import org.alephium.explorer.persistence.queries.{BlockQueries, InputUpdateQueries}
import org.alephium.explorer.util.{Scheduler, TimeUtil}
import org.alephium.protocol.model.BlockHash
import org.alephium.util.{Duration, TimeStamp}

/*
 * Syncing main chains blocks
 *
 * In the initialization phase, we make sure we get at least one timestamp other than the genesis one
 *
 * The syncing algorithm goes as follow:
 * 1. Getting maximum timestamps from both the local chains and the remote ones.
 * 2. Build timestamp ranges of X minutes each, starting from local max to remote max.
 * 3. For each of those range, we get all the blocks inbetween that time.
 * 4. Insert all blocks (with `mainChain = false`).
 * 5. For each last block of each chains, mark it as part of the main chain and travel
 *   down the parents recursively until we found back a parent that is part of the main chain.
 * 6. During step 5, if a parent is missing, we download it and continue the procces at 5.
 *
 * TODO: Step 5 is costly, but it's an easy way to handle reorg. In step 3 we know we receive the current main chain
 * for that timerange, so in step 4 we could directly insert them as `mainChain = true`, but we need to sync
 * to a sanity check process, wich could be an external proccess, that regularly goes down the chain to make
 * sure we have the right one in DB.
 */

@SuppressWarnings(Array("org.wartremover.warts.Var", "org.wartremover.warts.IterableOps"))
case object BlockFlowSyncService extends StrictLogging {
  // scalastyle:off magic.number
  private val defaultStep     = Duration.ofMinutesUnsafe(30L)
  private val defaultBackStep = Duration.ofSecondsUnsafe(10L)
  private val initialBackStep = Duration.ofMinutesUnsafe(30L)
  // scalastyle:on magic.number

  def start(nodeUris: ArraySeq[Uri], interval: FiniteDuration)(implicit ec: ExecutionContext,
                                                               dc: DatabaseConfig[PostgresProfile],
                                                               blockFlowClient: BlockFlowClient,
                                                               cache: BlockCache,
                                                               groupSetting: GroupSetting,
                                                               scheduler: Scheduler): Future[Unit] =
    scheduler.scheduleLoopConditional(
      taskId        = this.productPrefix,
      firstInterval = ScalaDuration.Zero,
      loopInterval  = interval,
      state         = new AtomicBoolean()
    )(init())(state => syncOnce(nodeUris, state))

  def syncOnce(nodeUris: ArraySeq[Uri], initialBackStepDone: AtomicBoolean)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      blockFlowClient: BlockFlowClient,
      cache: BlockCache,
      groupSetting: GroupSetting): Future[Unit] = {
    if (initialBackStepDone.get()) {
      syncOnceWith(nodeUris, defaultStep, defaultBackStep)
    } else {
      syncOnceWith(nodeUris, defaultStep, initialBackStep).map { _ =>
        initialBackStepDone.set(true)
      }
    }
  }

  // scalastyle:off magic.number
  private def syncOnceWith(nodeUris: ArraySeq[Uri], step: Duration, backStep: Duration)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      blockFlowClient: BlockFlowClient,
      cache: BlockCache,
      groupSetting: GroupSetting): Future[Unit] = {
    logger.debug("Start syncing")
    val startedAt  = TimeStamp.now()
    var downloaded = 0

    getTimeStampRange(step, backStep)
      .flatMap {
        case (ranges, nbOfBlocksToDownloads) =>
          logger.debug(s"Downloading $nbOfBlocksToDownloads blocks")
          Future.sequence {
            nodeUris.map { uri =>
              foldFutures(ranges) {
                case (from, to) =>
                  syncTimeRange(from, to, uri).map { num =>
                    synchronized {
                      downloaded = downloaded + num
                      logger.debug(s"Downloaded ${downloaded}, progress ${scala.math
                        .min(100, (downloaded.toFloat / nbOfBlocksToDownloads * 100.0).toInt)}%")
                    }
                  }
              }
            }
          }
      }
      .map { _ =>
        val duration = TimeStamp.now().deltaUnsafe(startedAt)
        logger.debug(s"Syncing done in ${duration.toMinutes} min")
      }
  }
  // scalastyle:on magic.number

  private def syncTimeRange(
      from: TimeStamp,
      to: TimeStamp,
      uri: Uri
  )(implicit ec: ExecutionContext,
    dc: DatabaseConfig[PostgresProfile],
    blockFlowClient: BlockFlowClient,
    cache: BlockCache,
    groupSetting: GroupSetting): Future[Int] = {
    blockFlowClient.fetchBlocks(from, to, uri).flatMap { multiChain =>
      for {
        res <- Future
          .sequence(multiChain.map(insertBlocks))
          .map(_.sum)
        _ <- dc.db.run(InputUpdateQueries.updateInputs())
      } yield res
    }
  }

  //We need at least one TimeStamp other than a genesis one
  def init()(implicit ec: ExecutionContext,
             dc: DatabaseConfig[PostgresProfile],
             blockFlowClient: BlockFlowClient,
             cache: BlockCache,
             groupSetting: GroupSetting): Future[Boolean] =
    run(BlockQueries.maxHeightZipped(groupSetting.groupIndexes)) flatMap { heightAndGroups =>
      Future
        .traverse(heightAndGroups) {
          case (height, (fromGroup, toGroup)) =>
            height match {
              case Some(height) if height.value == 0 =>
                syncAt(fromGroup, toGroup, Height.unsafe(1)).map(_.nonEmpty)
              case None =>
                for {
                  _      <- syncAt(fromGroup, toGroup, Height.unsafe(0))
                  blocks <- syncAt(fromGroup, toGroup, Height.unsafe(1))
                } yield blocks.nonEmpty
              case _ => Future.successful(true)
            }
        }
        .map(_.contains(true))
    }

  private def getTimeStampRange(step: Duration, backStep: Duration)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      blockFlowClient: BlockFlowClient,
      groupSetting: GroupSetting): Future[(ArraySeq[(TimeStamp, TimeStamp)], Int)] =
    for {
      localTs  <- getLocalMaxTimestamp()
      remoteTs <- getRemoteMaxTimestamp()
    } yield fetchAndBuildTimeStampRange(step, backStep, localTs, remoteTs)

  /** @see [[org.alephium.explorer.persistence.queries.BlockQueries.numOfBlocksAndMaxBlockTimestamp]] */
  def getLocalMaxTimestamp()(implicit ec: ExecutionContext,
                             dc: DatabaseConfig[PostgresProfile],
                             groupSetting: GroupSetting): Future[Option[(TimeStamp, Int)]] = {
    //Convert query result to return `Height` as `Int` value.
    val queryResultToIntHeight =
      BlockQueries.numOfBlocksAndMaxBlockTimestamp() map { optionResult =>
        optionResult map {
          case (timestamp, height) =>
            (timestamp, height.value)
        }
      }

    run(queryResultToIntHeight)
  }

  private def getRemoteMaxTimestamp()(
      implicit ec: ExecutionContext,
      blockFlowClient: BlockFlowClient,
      groupSetting: GroupSetting): Future[Option[(TimeStamp, Int)]] = {
    Future
      .sequence(groupSetting.groupIndexes.map {
        case (fromGroup, toGroup) =>
          blockFlowClient
            .fetchChainInfo(fromGroup, toGroup)
            .flatMap { chainInfo =>
              blockFlowClient
                .fetchBlocksAtHeight(fromGroup, toGroup, Height.unsafe(chainInfo.currentHeight))
                .map { blocks =>
                  blocks.map(_.timestamp).maxOption.map(ts => (ts, chainInfo.currentHeight))
                }
            }

      })
      .map { res =>
        val tsHeights  = res.flatten
        val nbOfBlocks = tsHeights.map { case (_, height) => height }.sum
        tsHeights.map { case (ts, _) => ts }.maxOption.map(max => (max, nbOfBlocks))
      }
  }

  @SuppressWarnings(Array("org.wartremover.warts.IterableOps"))
  private def syncAt(
      fromGroup: GroupIndex,
      toGroup: GroupIndex,
      height: Height
  )(implicit ec: ExecutionContext,
    dc: DatabaseConfig[PostgresProfile],
    blockFlowClient: BlockFlowClient,
    cache: BlockCache,
    groupSetting: GroupSetting): Future[Option[ArraySeq[BlockEntity]]] = {
    blockFlowClient
      .fetchBlocksAtHeight(fromGroup, toGroup, height)
      .flatMap { blocks =>
        if (blocks.nonEmpty) {
          val bestBlock = blocks.head // First block is the main chain one
          for {
            _ <- insert(bestBlock)
            _ <- BlockDao.updateLatestBlock(bestBlock)
          } yield Some(ArraySeq(bestBlock))
        } else {
          Future.successful(None)
        }
      }
  }

  private def updateMainChain(hash: BlockHash, chainFrom: GroupIndex, chainTo: GroupIndex)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      blockFlowClient: BlockFlowClient,
      cache: BlockCache,
      groupSetting: GroupSetting): Future[Unit] = {
    BlockDao.updateMainChain(hash, chainFrom, chainTo, groupSetting.groupNum).flatMap {
      case None          => Future.successful(())
      case Some(missing) => handleMissingMainChainBlock(missing, chainFrom)
    }
  }

  @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
  private def insert(block: BlockEntity)(implicit ec: ExecutionContext,
                                         dc: DatabaseConfig[PostgresProfile],
                                         blockFlowClient: BlockFlowClient,
                                         cache: BlockCache,
                                         groupSetting: GroupSetting): Future[Unit] = {
    (block.parent(groupSetting.groupNum) match {
      case Some(parent) =>
        //We make sure the parent is inserted before inserting the block
        run(BlockQueries.getBlockChainInfo(parent))
          .flatMap {
            case None =>
              handleMissingMainChainBlock(parent, block.chainFrom)
            case Some((chainFrom, chainTo, mainChain)) if !mainChain =>
              logger.debug(s"Parent $parent exist but is not mainChain")
              if (block.chainFrom == chainFrom && block.chainTo == chainTo) {
                updateMainChain(parent, block.chainFrom, block.chainTo)
              } else {
                val error =
                  BlocksInDifferentChains(parent          = parent,
                                          parentChainFrom = chainFrom,
                                          parentChainTo   = chainTo,
                                          child           = block)
                Future.failed(error)
              }
            case Some(_) => Future.successful(())
          }
      case None if block.height.value == 0 => Future.successful(())
      case None                            => Future.successful(logger.error(s"${block.hash} doesn't have a parent"))
    }).flatMap { _ =>
      for {
        _ <- BlockDao.insert(block)
        _ <- BlockDao.updateMainChain(block.hash,
                                      block.chainFrom,
                                      block.chainTo,
                                      groupSetting.groupNum)
      } yield ()
    }
  }

  private def insertBlocks(blocks: ArraySeq[BlockEntity])(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      blockFlowClient: BlockFlowClient,
      cache: BlockCache,
      groupSetting: GroupSetting): Future[Int] = {
    if (blocks.nonEmpty) {
      for {
        _ <- foldFutures(blocks)(insert)
        _ <- BlockDao.updateLatestBlock(blocks.last)
      } yield (blocks.size)
    } else {
      Future.successful(0)
    }
  }

  private def handleMissingMainChainBlock(missing: BlockHash, chainFrom: GroupIndex)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      blockFlowClient: BlockFlowClient,
      cache: BlockCache,
      groupSetting: GroupSetting): Future[Unit] = {
    logger.debug(s"Downloading missing block $missing")
    blockFlowClient.fetchBlock(chainFrom, missing).flatMap { block =>
      insert(block).map(_ => ())
    }
  }

  def fetchAndBuildTimeStampRange(
      step: Duration,
      backStep: Duration,
      localTs: Option[(TimeStamp, Int)],
      remoteTs: Option[(TimeStamp, Int)]): (ArraySeq[(TimeStamp, TimeStamp)], Int) = {
    (for {
      (localTs, localNbOfBlocks) <- localTs.map {
        case (ts, nb) => (ts.plusMillisUnsafe(1), nb)
      }
      (remoteTs, remoteNbOfBlocks) <- remoteTs.map {
        case (ts, nb) => (ts.plusMillisUnsafe(1), nb)
      }
    } yield {
      if (remoteTs.isBefore(localTs)) {
        logger.error("max remote ts can't be before local one")
        sys.exit(0)
      } else {
        (TimeUtil.buildTimestampRange(localTs.minusUnsafe(backStep), remoteTs, step),
         remoteNbOfBlocks - localNbOfBlocks)
      }
    }) match {
      case None      => (ArraySeq.empty, 0)
      case Some(res) => res
    }
  }
}
