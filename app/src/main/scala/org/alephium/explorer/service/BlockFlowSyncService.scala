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

import com.typesafe.scalalogging.StrictLogging
import org.alephium.explorer.{foldFutures, GroupSetting}
import org.alephium.explorer.api.model.Height
import org.alephium.explorer.cache.BlockCache
import org.alephium.explorer.persistence.DBRunner._
import org.alephium.explorer.persistence.queries.{BlockQueries, InputUpdateQueries}
import org.alephium.explorer.service.sync.SimersSyncPlayground
import org.alephium.explorer.util.{Scheduler, TimeUtil}
import org.alephium.util.{Duration, TimeStamp}
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile
import sttp.model.Uri

import java.util.concurrent.atomic.AtomicBoolean
import scala.collection.immutable.ArraySeq
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.{FiniteDuration, Duration => ScalaDuration}

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
    )(SimersSyncPlayground.initIgnoreError())(state => syncOnce(nodeUris, state))

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
          .sequence(multiChain.map(SimersSyncPlayground.insert))
          .map(_ => 0) //TODO should return the number of blocks synced
//          .map(_.sum)
        _ <- dc.db.run(InputUpdateQueries.updateInputs())
      } yield res
    }
  }

  private def getTimeStampRange(step: Duration, backStep: Duration)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      blockFlowClient: BlockFlowClient,
      groupSetting: GroupSetting): Future[(ArraySeq[(TimeStamp, TimeStamp)], Int)] = {
    fetchAndBuildTimeStampRange(step, backStep, getLocalMaxTimestamp(), getRemoteMaxTimestamp())
  }

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

  def fetchAndBuildTimeStampRange(
      step: Duration,
      backStep: Duration,
      fetchLocalTs:  => Future[Option[(TimeStamp, Int)]],
      fetchRemoteTs: => Future[Option[(TimeStamp, Int)]]
  )(implicit executionContext: ExecutionContext)
    : Future[(ArraySeq[(TimeStamp, TimeStamp)], Int)] = {
    for {
      localTs  <- fetchLocalTs
      remoteTs <- fetchRemoteTs
    } yield {
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
}
