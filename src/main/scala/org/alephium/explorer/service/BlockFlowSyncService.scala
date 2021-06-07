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

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

import com.typesafe.scalalogging.StrictLogging

import org.alephium.explorer.{sideEffect, AnyOps}
import org.alephium.explorer.api.model.{BlockEntry, GroupIndex, Height}
import org.alephium.explorer.persistence.dao.BlockDao
import org.alephium.explorer.persistence.model.BlockEntity
import org.alephium.util.Duration

trait BlockFlowSyncService {
  def start(): Future[Unit]
  def stop(): Future[Unit]
}

@SuppressWarnings(Array("org.wartremover.warts.Recursion", "org.wartremover.warts.Var"))
object BlockFlowSyncService {
  def apply(groupNum: Int,
            syncPeriod: Duration,
            blockFlowClient: BlockFlowClient,
            blockDao: BlockDao)(implicit executionContext: ExecutionContext): BlockFlowSyncService =
    new Impl(groupNum, syncPeriod, blockFlowClient, blockDao)

  private class Impl(groupNum: Int,
                     syncPeriod: Duration,
                     blockFlowClient: BlockFlowClient,
                     blockDao: BlockDao)(implicit executionContext: ExecutionContext)
      extends BlockFlowSyncService
      with StrictLogging {

    private val chainIndexes: Seq[(GroupIndex, GroupIndex)] = for {
      i <- 0 to groupNum - 1
      j <- 0 to groupNum - 1
    } yield (GroupIndex.unsafe(i), GroupIndex.unsafe(j))

    private val stopped: Promise[Unit]  = Promise()
    private val syncDone: Promise[Unit] = Promise()

    def start(): Future[Unit] =
      Future.successful(sync())

    def stop(): Future[Unit] = {
      sideEffect(if (!stopped.isCompleted) stopped.success(()))
      syncDone.future
    }

    private def sync(): Unit =
      syncOnce()
        .onComplete {
          case Success(_) =>
            logger.debug("Syncing done")
            if (stopped.isCompleted) {
              syncDone.success(())
            } else {
              Thread.sleep(syncPeriod.millis)
              sync()
            }
          case Failure(e) =>
            logger.error("Failure while syncing", e)
        }

    private var blocksToSync = 0.0
    private var downloaded   = 0.0

    private def syncOnce(): Future[Unit] = {
      blocksToSync = 0.0
      downloaded   = 0.0
      Future
        .traverse(chainIndexes) {
          case (fromGroup, toGroup) =>
            syncChain(fromGroup, toGroup)
        }
        .map(sideEffect)
    }

    private def syncChain(fromGroup: GroupIndex, toGroup: GroupIndex): Future[Unit] = {
      blockFlowClient.fetchChainInfo(fromGroup, toGroup).flatMap {
        case Right(nodeHeight) =>
          blockDao.maxHeight(fromGroup, toGroup).flatMap { maybeLocalHeight =>
            logger.debug(
              s"Syncing chain ${fromGroup.value} -> ${toGroup.value}. Heights: local = ${maybeLocalHeight
                .map(_.value)
                .getOrElse(-1)}, remote = ${nodeHeight.currentHeight}")
            val heights = generateHeights(maybeLocalHeight, Height.unsafe(nodeHeight.currentHeight))
            blocksToSync = blocksToSync + heights.size
            foldFutures(heights) { height =>
              syncAt(fromGroup, toGroup, height).map { _ =>
                synchronized {
                  downloaded = downloaded + 1.0
                  if (downloaded % 100 == 0 || downloaded == blocksToSync) {
                    logger.debug(
                      s"Syncing ${blocksToSync.toInt} blocks, got ${downloaded.toInt}, progress ${(downloaded / blocksToSync * 100.0).toInt}%")
                  }
                }
              }
            }
          }
        case Left(error) => Future.successful(logger.error(error))
      }
    }

    @SuppressWarnings(Array("org.wartremover.warts.TraversableOps"))
    private def syncAt(
        fromGroup: GroupIndex,
        toGroup: GroupIndex,
        height: Height
    ): Future[Unit] = {
      blockFlowClient.fetchBlocksAtHeight(fromGroup, toGroup, height).flatMap {
        case Right(blocks) if blocks.nonEmpty =>
          val bestBlock = blocks.head
          for {
            _ <- syncBlocks(blocks)
            _ <- updateMainChainForBlock(bestBlock)
          } yield ()
        case Right(_)     => Future.successful(())
        case Left(errors) => Future.successful(errors.foreach(logger.error(_)))
      }
    }

    private def syncBlocks(blocks: Seq[BlockEntity]): Future[Unit] =
      foldFutures[BlockEntity](blocks)(block => syncBlock(block))

    private def syncBlock(block: BlockEntity): Future[Unit] = {
      blockDao.insert(block).flatMap { _ =>
        if (block.isGenesis) {
          Future.successful(())
        } else {
          block.parent(groupNum) match {
            case None =>
              Future.successful(logger.error(
                s"Cannot compute parent from hash: ${block.hash}, groupNum: $groupNum, chainTo: ${block.chainTo}"))
            case Some(parent) =>
              blockDao.get(parent).flatMap {
                case None =>
                  blockFlowClient.fetchBlock(block.chainFrom, parent).flatMap {
                    case Left(error)   => Future.successful(logger.error(error))
                    case Right(parent) => syncBlock(parent)
                  }
                case Some(_) => Future.successful(())
              }
          }
        }
      }
    }

    private def updateMainChainForHash(mainHash: BlockEntry.Hash): Future[Unit] = {
      blockDao.get(mainHash).flatMap {
        case Some(block) if !block.mainChain => updateMainChainForBlock(block)
        case _                               => Future.successful(())
      }
    }

    private def updateMainChainForBlock(newMain: FlowEntity): Future[Unit] = {
      require(!newMain.mainChain)
      for {
        blocksAtHeight <- blockDao.getAtHeight(newMain.chainFrom, newMain.chainTo, newMain.height)
        _              <- updateMainChainForHeight(newMain, blocksAtHeight)
        _              <- updateMainChainForParent(newMain)
      } yield ()
    }

    private def updateMainChainForHeight(newMain: FlowEntity,
                                         blocksAtHeight: Seq[BlockEntry]): Future[Unit] = {
      blocksAtHeight.find(_.mainChain) match {
        case Some(currentMain) =>
          for {
            _ <- blockDao.updateMainChainStatus(currentMain.hash, false)
            _ <- blockDao.updateMainChainStatus(newMain.hash, true)
          } yield ()
        case None =>
          blockDao.updateMainChainStatus(newMain.hash, true)
      }
    }

    private def updateMainChainForParent(newMain: FlowEntity): Future[Unit] = {
      if (newMain.height === Height.genesis) { // genesis
        Future.successful(())
      } else {
        newMain.parent(groupNum) match {
          case Some(parent) => updateMainChainForHash(parent)
          case None         => Future.successful(())
        }
      }
    }

    private def generateHeights(maybeLocal: Option[Height], remote: Height): Seq[Height] =
      maybeLocal match {
        case Some(local) =>
          if (local === remote) {
            Seq.empty
          } else {
            ((local.value + 1) to remote.value).toSeq.map(Height.unsafe)
          }
        case None =>
          (0 to remote.value).toSeq.map(Height.unsafe)
      }

    private def foldFutures[A](seqA: Seq[A])(f: A => Future[Unit]): Future[Unit] =
      seqA.foldLeft(Future.successful(())) {
        case (acc, a) => acc.flatMap(_ => f(a))
      }
  }
}
