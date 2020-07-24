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

@SuppressWarnings(Array("org.wartremover.warts.Recursion"))
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

    private def syncOnce(): Future[Unit] = {
      Future
        .traverse(chainIndexes) {
          case (fromGroup, toGroup) =>
            syncChain(fromGroup, toGroup)
        }
        .flatMap(_ => blockDao.updateSpent())
    }

    private def syncChain(fromGroup: GroupIndex, toGroup: GroupIndex): Future[Unit] = {
      blockFlowClient.getChainInfo(fromGroup, toGroup).flatMap {
        case Right(nodeHeight) =>
          blockDao.maxHeight(fromGroup, toGroup).flatMap { maybeLocalHeight =>
            logger.debug(
              s"Syncing (${fromGroup.value}, ${toGroup.value}). Heights: local = ${maybeLocalHeight
                .map(_.value)
                .getOrElse(-1)}, node = ${nodeHeight.currentHeight.value}")
            val heights = generateHeights(maybeLocalHeight, nodeHeight.currentHeight)
            foldFutures(heights)(height => syncAt(fromGroup, toGroup, height))
          }
        case Left(error) => Future.successful(logger.error(error))
      }
    }

    private def syncAt(
        fromGroup: GroupIndex,
        toGroup: GroupIndex,
        height: Height
    ): Future[Unit] = {
      blockFlowClient.getBlocksAtHeight(fromGroup, toGroup, height).flatMap {
        case Right(blocks) =>
          val maybeBestHash = blocks.headOption.map(_.hash)
          for {
            _ <- syncBlocks(blocks, maybeBestHash)
            _ <- updateMainChain(blocks, maybeBestHash)
          } yield ()
        case Left(errors) => Future.successful(errors.foreach(logger.error(_)))
      }
    }

    private def syncBlocks(blocks: Seq[BlockEntity],
                           maybeBestHash: Option[BlockEntry.Hash]): Future[Unit] =
      foldFutures[BlockEntity](blocks)(block =>
        syncBlock(block.copy(mainChain = maybeBestHash.contains(block.hash))))

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
                  blockFlowClient.getBlock(block.chainFrom, parent).flatMap {
                    case Left(error)   => Future.successful(logger.error(error))
                    case Right(parent) => syncBlock(parent)
                  }
                case Some(_) => Future.successful(())
              }
          }
        }
      }
    }

    private def getParent(blockEntry: BlockEntry): Future[Option[BlockEntry]] =
      blockEntry.parent(groupNum) match {
        case None             => Future.successful(None)
        case Some(parentHash) => blockDao.get(parentHash)
      }

    private def updateMainChain(blocks: Seq[BlockEntity],
                                maybeBestHash: Option[BlockEntry.Hash]): Future[Unit] =
      (for {
        bestHash  <- maybeBestHash
        bestBlock <- blocks.find(_.hash === bestHash)
      } yield bestBlock) match {
        case None => Future.successful(())
        case Some(bestBlock) =>
          if (bestBlock.height === Height.zero) { // genesis
            Future.successful(())
          } else {
            bestBlock.parent(groupNum) match {
              case None         => Future.successful(())
              case Some(parent) => updateMainChainForHash(parent)
            }
          }
      }

    private def updateMainChainForHash(hash: BlockEntry.Hash): Future[Unit] = {
      blockDao.get(hash).flatMap {
        case None        => Future.successful(())
        case Some(block) => updateMainChainForBlock(block)
      }
    }

    private def updateMainChainForBlock(newMain: BlockEntry): Future[Unit] = {
      blockDao.getAtHeight(newMain.chainFrom, newMain.chainTo, newMain.height).flatMap { blocks =>
        blocks.find(_.mainChain) match {
          case Some(currentMain) =>
            if (currentMain =/= newMain) {
              for {
                _      <- blockDao.updateMainChainStatus(newMain.hash, true)
                _      <- blockDao.updateMainChainStatus(currentMain.hash, false)
                parent <- getParent(newMain)
                _      <- parent.map(updateMainChainForBlock).getOrElse(Future.successful(()))
              } yield ()
            } else {
              //no fork
              Future.successful(())
            }
          case None => Future.successful(())
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
