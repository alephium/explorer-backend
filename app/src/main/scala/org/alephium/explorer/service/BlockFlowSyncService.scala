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

import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future}

import akka.http.scaladsl.model.Uri
import com.typesafe.scalalogging.StrictLogging

import org.alephium.explorer.api.model.{BlockEntry, GroupIndex, Height}
import org.alephium.explorer.foldFutures
import org.alephium.explorer.persistence.dao.BlockDao
import org.alephium.explorer.persistence.model.{BlockEntity, InputEntity}
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

trait BlockFlowSyncService extends SyncService.BlockFlow

@SuppressWarnings(Array("org.wartremover.warts.Var", "org.wartremover.warts.TraversableOps"))
object BlockFlowSyncService extends StrictLogging {
  def apply(groupNum: Int,
            syncPeriod: Duration,
            blockFlowClient: BlockFlowClient,
            blockDao: BlockDao)(implicit executionContext: ExecutionContext): BlockFlowSyncService =
    new Impl(groupNum, syncPeriod, blockFlowClient, blockDao)

  private class Impl(groupNum: Int,
                     val syncPeriod: Duration,
                     blockFlowClient: BlockFlowClient,
                     blockDao: BlockDao)(implicit val executionContext: ExecutionContext)
      extends BlockFlowSyncService {

    private val chainIndexes: Seq[(GroupIndex, GroupIndex)] = for {
      i <- 0 to groupNum - 1
      j <- 0 to groupNum - 1
    } yield (GroupIndex.unsafe(i), GroupIndex.unsafe(j))

    // scalastyle:off magic.number
    private val defaultStep     = Duration.ofMinutesUnsafe(30L)
    private val defaultBackStep = Duration.ofSecondsUnsafe(10L)
    private val initialBackStep = Duration.ofMinutesUnsafe(30L)

    private var initialBackStepDone = false
    // scalastyle:on magic.number

    override def syncOnce(): Future[Unit] = {
      if (initialBackStepDone) {
        syncOnceWith(defaultStep, defaultBackStep)
      } else {
        syncOnceWith(defaultStep, initialBackStep).map { _ =>
          initialBackStepDone = true
        }
      }
    }

    // scalastyle:off magic.number
    private def syncOnceWith(step: Duration, backStep: Duration): Future[Unit] = {
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
    ): Future[Int] = {
      blockFlowClient.fetchBlocks(from, to, uri).flatMap {
        case Right(multiChain) =>
          Future
            .sequence(multiChain.map(insertBlocks))
            .map(_.sum)
        case Left(error) =>
          logger.error(error)
          Future.successful(0)
      }
    }

    //We need at least one TimeStamp other than a genesis one
    override def init(): Future[Boolean] = {
      Future
        .traverse(chainIndexes) {
          case (fromGroup, toGroup) =>
            blockDao.maxHeight(fromGroup, toGroup).flatMap {
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

    private def getTimeStampRange(
        step: Duration,
        backStep: Duration): Future[(Seq[(TimeStamp, TimeStamp)], Int)] = {
      fetchAndBuildTimeStampRange(step, backStep, getLocalMaxTimestamp(), getRemoteMaxTimestamp())
    }

    private def getLocalMaxTimestamp(): Future[Option[(TimeStamp, Int)]] = {
      Future
        .traverse(chainIndexes) {
          case (fromGroup, toGroup) =>
            blockDao
              .maxHeight(fromGroup, toGroup)
              .flatMap {
                case Some(height) =>
                  blockDao
                    .getAtHeight(fromGroup, toGroup, height)
                    .map { blocks =>
                      blocks.map(_.timestamp).maxOption.map(ts => (ts, height))
                    }
                case None =>
                  Future.successful(None)
              }

        }
        .map { res =>
          val tsHeights  = res.flatten
          val nbOfBlocks = tsHeights.map { case (_, height) => height.value }.sum
          tsHeights.map { case (ts, _) => ts }.maxOption.map(max => (max, nbOfBlocks))
        }
    }

    private def getRemoteMaxTimestamp(): Future[Option[(TimeStamp, Int)]] = {
      Future
        .sequence(chainIndexes.map {
          case (fromGroup, toGroup) =>
            blockFlowClient
              .fetchChainInfo(fromGroup, toGroup)
              .flatMap {
                case Right(chainInfo) =>
                  blockFlowClient
                    .fetchBlocksAtHeight(fromGroup, toGroup, Height.unsafe(chainInfo.currentHeight))
                    .map {
                      case Right(blocks) =>
                        blocks.map(_.timestamp).maxOption.map(ts => (ts, chainInfo.currentHeight))
                      case Left(errors) =>
                        errors.foreach(logger.error(_))
                        None
                    }
                case Left(error) =>
                  logger.error(error)
                  Future.successful(None)
              }

        })
        .map { res =>
          val tsHeights  = res.flatten
          val nbOfBlocks = tsHeights.map { case (_, height) => height }.sum
          tsHeights.map { case (ts, _) => ts }.maxOption.map(max => (max, nbOfBlocks))
        }
    }

    @SuppressWarnings(Array("org.wartremover.warts.TraversableOps"))
    private def syncAt(
        fromGroup: GroupIndex,
        toGroup: GroupIndex,
        height: Height
    ): Future[Option[Seq[BlockEntity]]] = {
      blockFlowClient
        .fetchBlocksAtHeight(fromGroup, toGroup, height)
        .flatMap {
          case Right(blocks) if blocks.nonEmpty =>
            val bestBlock = blocks.head // First block is the main chain one
            for {
              _ <- insert(bestBlock)
              _ <- blockDao.updateLatestBlock(bestBlock)
            } yield Some(Seq(bestBlock))
          case Right(_) => Future.successful(None)
          case Left(errors) =>
            errors.foreach(logger.error(_))
            Future.successful(None)
        }
    }

    private def updateMainChain(hash: BlockEntry.Hash,
                                chainFrom: GroupIndex,
                                chainTo: GroupIndex): Future[Unit] = {
      blockDao.updateMainChain(hash, chainFrom, chainTo, groupNum).flatMap {
        case None          => Future.successful(())
        case Some(missing) => handleMissingMainChainBlock(missing, chainFrom)
      }
    }

    @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
    private def insert(block: BlockEntity): Future[Seq[InputEntity]] = {
      (block.parent(groupNum) match {
        case Some(parent) =>
          //We make sure the parent is inserted before inserting the block
          blockDao
            .get(parent)
            .flatMap {
              case None =>
                handleMissingMainChainBlock(parent, block.chainFrom)
              case Some(parentBlock) if !parentBlock.mainChain =>
                logger.debug(s"Parent $parent exist but is not mainChain")
                assert(
                  block.chainFrom == parentBlock.chainFrom && block.chainTo == parentBlock.chainTo)
                updateMainChain(parentBlock.hash, block.chainFrom, block.chainTo)
              case Some(_) => Future.successful(())
            }
        case None if block.height.value == 0 => Future.successful(())
        case None                            => Future.successful(logger.error(s"${block.hash} doesn't have a parent"))
      }).flatMap { _ =>
        for {
          _              <- blockDao.insert(block)
          inputsToUpdate <- blockDao.updateTransactionPerAddress(block)
          _              <- blockDao.updateMainChain(block.hash, block.chainFrom, block.chainTo, groupNum)
        } yield (inputsToUpdate)
      }
    }

    private def insertBlocks(blocks: Seq[BlockEntity]): Future[Int] = {
      if (blocks.nonEmpty) {
        for {
          inputsToUpdate <- foldFutures(blocks)(insert)
          _              <- blockDao.updateLatestBlock(blocks.last)
          _              <- handleInputsToUpdate(inputsToUpdate.flatten)
        } yield (blocks.size)
      } else {
        Future.successful(0)
      }
    }

    private def handleInputsToUpdate(inputs: Seq[InputEntity]) = {
      if (inputs.nonEmpty) {
        blockDao.updateInputs(inputs).map(_ => ())
      } else {
        Future.successful(())
      }
    }

    private def handleMissingMainChainBlock(missing: BlockEntry.Hash,
                                            chainFrom: GroupIndex): Future[Unit] = {
      logger.debug(s"Downloading missing block $missing")
      blockFlowClient.fetchBlock(chainFrom, missing).flatMap {
        case Left(error) => Future.successful(logger.error(error))
        case Right(block) =>
          insert(block).map(_ => ())
      }
    }
  }

  def buildTimestampRange(localTs: TimeStamp,
                          remoteTs: TimeStamp,
                          step: Duration): Seq[(TimeStamp, TimeStamp)] = {
    @tailrec
    def rec(l: TimeStamp, seq: Seq[(TimeStamp, TimeStamp)]): Seq[(TimeStamp, TimeStamp)] = {
      val next = l + step
      if (next.isBefore(remoteTs)) {
        rec(next.plusMillisUnsafe(1), seq :+ ((l, next)))
      } else if (l == remoteTs) {
        seq :+ ((remoteTs, remoteTs))
      } else {
        seq :+ ((l, remoteTs))
      }
    }

    if (remoteTs.millis <= localTs.millis || step == Duration.zero) {
      Seq.empty
    } else {
      rec(localTs, Seq.empty)
    }
  }

  def fetchAndBuildTimeStampRange(
      step: Duration,
      backStep: Duration,
      fetchLocalTs:  => Future[Option[(TimeStamp, Int)]],
      fetchRemoteTs: => Future[Option[(TimeStamp, Int)]]
  )(implicit executionContext: ExecutionContext): Future[(Seq[(TimeStamp, TimeStamp)], Int)] = {
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
          (buildTimestampRange(localTs.minusUnsafe(backStep), remoteTs, step),
           remoteNbOfBlocks - localNbOfBlocks)
        }
      }) match {
        case None      => (Seq.empty, 0)
        case Some(res) => res
      }
    }
  }
}
