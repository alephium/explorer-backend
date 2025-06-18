// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.service

import java.util.concurrent.atomic.AtomicBoolean

import scala.collection.immutable.ArraySeq
import scala.concurrent.{ExecutionContext, Future}
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
 *
 * TODO: Step 5 is costly, but it's an easy way to handle reorg. In step 3 we know we receive the current main chain
 * for that timerange, so in step 4 we could directly insert them as `mainChain = true`, but we need to sync
 * to a sanity check process, wich could be an external proccess, that regularly goes down the chain to make
 * sure we have the right one in DB.
 */

@SuppressWarnings(Array("org.wartremover.warts.Var", "org.wartremover.warts.IterableOps"))
case object BlockFlowSyncService extends StrictLogging {
  // scalastyle:off magic.number
  private val defaultBackStep = Duration.ofSecondsUnsafe(10L)
  private val initialBackStep = Duration.ofMinutesUnsafe(30L)
  // scalastyle:on magic.number

  def start(nodeUris: ArraySeq[Uri], interval: FiniteDuration, blockFlowFetchMaxAge: Duration)(
      implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      blockFlowClient: BlockFlowClient,
      cache: BlockCache,
      groupSetting: GroupSetting,
      scheduler: Scheduler
  ): Future[Unit] =
    scheduler.scheduleLoopConditional(
      taskId = this.productPrefix,
      firstInterval = ScalaDuration.Zero,
      loopInterval = interval,
      state = new AtomicBoolean()
    )(init())(state => syncOnce(nodeUris, state, blockFlowFetchMaxAge))

  def syncOnce(
      nodeUris: ArraySeq[Uri],
      initialBackStepDone: AtomicBoolean,
      blockFlowFetchMaxAge: Duration
  )(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      blockFlowClient: BlockFlowClient,
      cache: BlockCache,
      groupSetting: GroupSetting
  ): Future[Unit] = {
    if (initialBackStepDone.get()) {
      syncOnceWith(nodeUris, blockFlowFetchMaxAge, defaultBackStep)
    } else {
      syncOnceWith(nodeUris, blockFlowFetchMaxAge, initialBackStep).map { _ =>
        initialBackStepDone.set(true)
      }
    }
  }

  // scalastyle:off magic.number
  private def syncOnceWith(
      nodeUris: ArraySeq[Uri],
      step: Duration,
      backStep: Duration
  )(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      blockFlowClient: BlockFlowClient,
      cache: BlockCache,
      groupSetting: GroupSetting
  ): Future[Unit] = {
    getTimeStampRange(step, backStep)
      .flatMap { ranges =>
        Future.sequence {
          nodeUris.map { uri =>
            foldFutures(ranges) { case (from, to) =>
              logger.debug(
                s"Syncing from ${TimeUtil.toInstant(from)} to ${TimeUtil
                    .toInstant(to)} (${from.millis} - ${to.millis})"
              )
              syncTimeRange(from, to, uri)
            }
          }
        }
      }
      .map { nbs =>
        logger.debug(s"Synced ${nbs.flatten.sum} blocks")
      }

  }
  // scalastyle:on magic.number

  private def syncTimeRange(
      from: TimeStamp,
      to: TimeStamp,
      uri: Uri
  )(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      blockFlowClient: BlockFlowClient,
      cache: BlockCache,
      groupSetting: GroupSetting
  ): Future[Int] = {
    blockFlowClient.fetchBlocks(from, to, uri).flatMap { multiChain =>
      for {
        res <- Future
          .sequence(multiChain.map(insertBlocks))
          .map(_.sum)
        _ <- dc.db.run(InputUpdateQueries.updateInputs())
        _ <- updateMetadatas(blockFlowClient)
      } yield res
    }
  }

  private def updateMetadatas(blockFlowClient: BlockFlowClient)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[Unit] = {
    blockFlowClient.fetchSelfClique().flatMap { selfClique =>
      if (selfClique.synced) {
        for {
          _ <- TokenService.updateContractsMetadata(blockFlowClient)
          _ <- TokenService.updateTokensMetadata(blockFlowClient)
        } yield ()
      } else {
        Future.unit
      }
    }
  }

  // We need at least one TimeStamp other than a genesis one
  def init()(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      blockFlowClient: BlockFlowClient,
      cache: BlockCache,
      groupSetting: GroupSetting
  ): Future[Boolean] =
    cache.getAllLatestBlocks().flatMap { latestBlocks =>
      Future
        .traverse(groupSetting.chainIndexes) { chainIndex =>
          latestBlocks.collectFirst {
            case (index, block) if index == chainIndex => block.height
          } match {
            case None =>
              initFromScratch(chainIndex)
            case Some(height) if height.value == -1 =>
              initFromScratch(chainIndex)
            case Some(height) if height.value == 0 =>
              syncAt(chainIndex, Height.unsafe(1)).map(_.nonEmpty)
            case _ => Future.successful(true)
          }
        }
        .map(_.contains(true))
    }

  private def initFromScratch(chainIndex: ChainIndex)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      blockFlowClient: BlockFlowClient,
      cache: BlockCache,
      groupSetting: GroupSetting
  ) =
    for {
      _      <- syncAt(chainIndex, Height.unsafe(0))
      blocks <- syncAt(chainIndex, Height.unsafe(1))
    } yield blocks.nonEmpty

  private def getTimeStampRange(step: Duration, backStep: Duration)(implicit
      ec: ExecutionContext,
      blockFlowClient: BlockFlowClient,
      cache: BlockCache,
      groupSetting: GroupSetting
  ): Future[ArraySeq[(TimeStamp, TimeStamp)]] =
    for {
      localTs  <- getLocalMaxTimestamp()
      remoteTs <- getRemoteMaxTimestamp(localTs, step)
      result <- TimeUtil.buildTimeStampRangeOrEmpty(step, backStep, localTs, remoteTs) match {
        case Failure(exception) =>
          Future.failed(exception)
        case Success(result) =>
          Future.successful(result)
      }
    } yield result

  def getLocalMaxTimestamp()(implicit
      ec: ExecutionContext,
      cache: BlockCache,
      groupSetting: GroupSetting
  ): Future[Option[TimeStamp]] = {
    cache.getAllLatestBlocks().map { chainIndexlatestBlocks =>
      chainIndexlatestBlocks.map { case (_, latestBlock) => latestBlock }.map(_.timestamp).maxOption
    }
  }

  /** Returns the maximum timestamp we can use to fetch blocks from the remote node.
    */
  private def getRemoteMaxTimestamp(localTsOpt: Option[TimeStamp], step: Duration)(implicit
      ec: ExecutionContext,
      blockFlowClient: BlockFlowClient,
      groupSetting: GroupSetting
  ): Future[Option[TimeStamp]] = {
    localTsOpt match {
      case Some(locatTs) =>
        val now = TimeStamp.now()
        if (locatTs.plusUnsafe(step) >= now) {
          // We know that we can fetch blocks from the remote node in one time range
          Future.successful(Some(now))
        } else {
          // We can't fetch all blocks in one go, so we need to ask the remote node what is it's latest timestamp
          // Otherwise we might try to fetch lots of empty timestamps if the remote node is not synced
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
              val tsHeights = res.flatten
              tsHeights.map { case (ts, _) => ts }.maxOption
            }

        }
      case None => Future.successful(None)
    }
  }

  @SuppressWarnings(Array("org.wartremover.warts.IterableOps"))
  private def syncAt(
      chainIndex: ChainIndex,
      height: Height
  )(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      blockFlowClient: BlockFlowClient,
      cache: BlockCache,
      groupSetting: GroupSetting
  ): Future[Option[ArraySeq[BlockEntity]]] = {
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

  private def updateMainChain(hash: BlockHash, chainFrom: GroupIndex, chainTo: GroupIndex)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      blockFlowClient: BlockFlowClient,
      cache: BlockCache,
      groupSetting: GroupSetting
  ): Future[Unit] = {
    BlockDao.updateMainChain(hash, chainFrom, chainTo, groupSetting.groupNum).flatMap {
      case None          => Future.successful(())
      case Some(missing) => handleMissingMainChainBlock(missing, chainFrom)
    }
  }

  private def insertWithEvents(blockWithEvents: BlockEntityWithEvents)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      blockFlowClient: BlockFlowClient,
      cache: BlockCache,
      groupSetting: GroupSetting
  ): Future[Unit] = {
    insert(blockWithEvents.block, blockWithEvents.events)
  }

  @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
  private def insert(block: BlockEntity, events: ArraySeq[EventEntity])(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      blockFlowClient: BlockFlowClient,
      cache: BlockCache,
      groupSetting: GroupSetting
  ): Future[Unit] = {
    (block.parent(groupSetting.groupNum) match {
      case Some(parent) =>
        // We make sure the parent is inserted before inserting the block
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
                  BlocksInDifferentChains(
                    parent = parent,
                    parentChainFrom = chainFrom,
                    parentChainTo = chainTo,
                    child = block
                  )
                Future.failed(error)
              }
            case Some(_) => Future.successful(())
          }
      case None if block.height.value == 0 => Future.successful(())
      case None => Future.successful(logger.error(s"${block.hash} doesn't have a parent"))
    }).flatMap { _ =>
      for {
        _ <- BlockDao.insertWithEvents(block, events)
        _ <- handleUncles(block.ghostUncles.map(_.blockHash), block.chainFrom)
        _ <- BlockDao.updateMainChain(
          block.hash,
          block.chainFrom,
          block.chainTo,
          groupSetting.groupNum
        )
      } yield ()
    }
  }

  private def insertBlocks(blocksWithEvents: ArraySeq[BlockEntityWithEvents])(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      blockFlowClient: BlockFlowClient,
      cache: BlockCache,
      groupSetting: GroupSetting
  ): Future[Int] = {
    if (blocksWithEvents.nonEmpty) {
      for {
        _ <- foldFutures(blocksWithEvents)(insertWithEvents)
        _ <- BlockDao.updateLatestBlock(blocksWithEvents.last.block)
      } yield {
        blocksWithEvents.size
      }
    } else {
      Future.successful(0)
    }
  }

  private def handleMissingMainChainBlock(missing: BlockHash, chainFrom: GroupIndex)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      blockFlowClient: BlockFlowClient,
      cache: BlockCache,
      groupSetting: GroupSetting
  ): Future[Unit] = {
    logger.debug(s"Downloading missing block $missing")
    blockFlowClient.fetchBlockAndEvents(chainFrom, missing).flatMap(insertWithEvents)
  }

  // Ghost uncle blocks are only insterted in the database, we don't update the main chain
  private def handleUncles(uncles: ArraySeq[BlockHash], chainFrom: GroupIndex)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      blockFlowClient: BlockFlowClient,
      groupSetting: GroupSetting
  ): Future[Unit] = {
    if (uncles.nonEmpty) {
      logger.trace(s"Downloading ghost uncles ${uncles}")
      Future
        .sequence(uncles.map { uncle =>
          blockFlowClient
            .fetchBlockAndEvents(chainFrom, uncle)
            .flatMap(bwe => BlockDao.insertWithEvents(bwe.block, bwe.events))
        })
        .map(_ => ())
    } else {
      Future.successful(())
    }
  }
}
