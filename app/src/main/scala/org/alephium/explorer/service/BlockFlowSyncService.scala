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
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.concurrent.duration.{Duration => ScalaDuration, FiniteDuration}
import scala.util.{Failure, Success}

import com.typesafe.scalalogging.StrictLogging
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile
import sttp.model.Uri

import org.alephium.explorer.{foldFutures, GroupSetting}
import org.alephium.explorer.api.model.Height
import org.alephium.explorer.cache.BlockCache
import org.alephium.explorer.error.ExplorerError.BlocksInDifferentChains
import org.alephium.explorer.persistence.DBRunner._
import org.alephium.explorer.persistence.dao.BlockDao
import org.alephium.explorer.persistence.model.{BlockEntity, BlockEntityWithEvents, EventEntity}
import org.alephium.explorer.persistence.queries.{BlockQueries, InputUpdateQueries}
import org.alephium.explorer.util.{Scheduler, TimeUtil}
import org.alephium.protocol.model.{BlockHash, ChainIndex, GroupIndex}
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
 * 7. Once the blocks are up-to-date with the node, we switch to websocket syncing
 * 8. If the websocket close or is late, in case of network issue, we go back to step 1.
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
  private val upToDateDelta   = Duration.ofSecondsUnsafe(30L)
  // scalastyle:on magic.number

  def start(nodeUris: ArraySeq[Uri], interval: FiniteDuration, blockflowWsUri: Uri)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      blockFlowClient: BlockFlowClient,
      cache: BlockCache,
      groupSetting: GroupSetting,
      scheduler: Scheduler): Future[Unit] =
    scheduler.scheduleLoopConditional(
      taskId        = this.productPrefix,
      firstInterval = ScalaDuration.Zero,
      loopInterval  = interval,
      state         = new AtomicBoolean(),
      stop          = None
    )(init())(state => syncOnce(nodeUris, state, blockflowWsUri))

  def syncOnce(nodeUris: ArraySeq[Uri], initialBackStepDone: AtomicBoolean, blockflowWsUri: Uri)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      blockFlowClient: BlockFlowClient,
      cache: BlockCache,
      groupSetting: GroupSetting): Future[Unit] = {
    val syncResult =
      if (initialBackStepDone.get()) {
        syncOnceWith(nodeUris, defaultStep, defaultBackStep)
      } else {
        syncOnceWith(nodeUris, defaultStep, initialBackStep).map { result =>
          initialBackStepDone.set(true)
          result
        }
      }

    syncResult.flatMap { isUpToDate =>
      if (isUpToDate) {
        logger.info("Blocks are up to date, switching to web socket syncing")
        val stopPromise = Promise[Unit]()
        WebSocketSyncService.sync(blockflowWsUri, stopPromise)
        stopPromise.future.map { _ =>
          logger.info("WebSocket syncing stopped, resuming http syncing")
        }
      } else {
        Future.successful(())
      }
    }
  }

  // scalastyle:off magic.number
  def syncOnceWith(nodeUris: ArraySeq[Uri], step: Duration, backStep: Duration)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      blockFlowClient: BlockFlowClient,
      cache: BlockCache,
      groupSetting: GroupSetting): Future[Boolean] = {
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

                      (TimeStamp.now() -- to).map(_ < upToDateDelta).getOrElse(false)
                    }
                  }
              }
            }
          }
      }
      .map { deltas =>
        val duration = TimeStamp.now().deltaUnsafe(startedAt)
        logger.debug(s"Syncing done in ${duration.toMinutes} min")

        deltas.flatten.contains(true)
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
    run(BlockQueries.maxHeightZipped(groupSetting.chainIndexes)) flatMap { heightAndGroups =>
      Future
        .traverse(heightAndGroups) {
          case (height, chainIndex) =>
            height match {
              case Some(height) if height.value == 0 =>
                syncAt(chainIndex, Height.unsafe(1)).map(_.nonEmpty)
              case None =>
                for {
                  _      <- syncAt(chainIndex, Height.unsafe(0))
                  blocks <- syncAt(chainIndex, Height.unsafe(1))
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
      result <- TimeUtil.buildTimeStampRangeOrEmpty(step, backStep, localTs, remoteTs) match {
        case Failure(exception) =>
          Future.failed(exception)
        case Success(result) =>
          Future.successful(result)
      }
    } yield result

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
      .sequence(groupSetting.chainIndexes.map { chainIndex =>
        blockFlowClient
          .fetchChainInfo(chainIndex)
          .flatMap { chainInfo =>
            blockFlowClient
              .fetchBlocksAtHeight(chainIndex, Height.unsafe(chainInfo.currentHeight))
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
      chainIndex: ChainIndex,
      height: Height
  )(implicit ec: ExecutionContext,
    dc: DatabaseConfig[PostgresProfile],
    blockFlowClient: BlockFlowClient,
    cache: BlockCache,
    groupSetting: GroupSetting): Future[Option[ArraySeq[BlockEntity]]] = {
    blockFlowClient
      .fetchBlocksAtHeight(chainIndex, height)
      .flatMap { blocks =>
        if (blocks.nonEmpty) {
          val bestBlock = blocks.head // First block is the main chain one
          for {
            _ <- insert(bestBlock, ArraySeq.empty)
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

  private def insertWithEvents(blockWithEvents: BlockEntityWithEvents)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      blockFlowClient: BlockFlowClient,
      cache: BlockCache,
      groupSetting: GroupSetting): Future[Unit] = {
    insert(blockWithEvents.block, blockWithEvents.events)
  }

  @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
  private def insert(block: BlockEntity, events: ArraySeq[EventEntity])(
      implicit ec: ExecutionContext,
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
        _ <- BlockDao.insertWithEvents(block, events)
        _ <- BlockDao.updateMainChain(block.hash,
                                      block.chainFrom,
                                      block.chainTo,
                                      groupSetting.groupNum)
      } yield ()
    }
  }

  def insertBlocks(blocksWithEvents: ArraySeq[BlockEntityWithEvents])(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      blockFlowClient: BlockFlowClient,
      cache: BlockCache,
      groupSetting: GroupSetting): Future[Int] = {
    if (blocksWithEvents.nonEmpty) {
      for {
        _ <- foldFutures(blocksWithEvents)(insertWithEvents)
        _ <- BlockDao.updateLatestBlock(blocksWithEvents.last.block)
      } yield (blocksWithEvents.size)
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
    blockFlowClient.fetchBlockAndEvents(chainFrom, missing).flatMap(insertWithEvents)
  }
}
