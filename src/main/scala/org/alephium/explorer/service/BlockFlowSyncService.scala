package org.alephium.explorer.service

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

import com.typesafe.scalalogging.StrictLogging

import org.alephium.explorer.{sideEffect, AnyOps}
import org.alephium.explorer.api.model.{BlockEntry, GroupIndex, Height}
import org.alephium.explorer.persistence.dao.BlockDao
import org.alephium.util.Duration

trait BlockFlowSyncService {
  def start(): Future[Unit]
  def stop(): Future[Unit]
}

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
      sideEffect(stopped.success(()))
      syncDone.future
    }

    @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
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
        .map(_ => ())
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
            Future.traverse(heights)(syncAt(fromGroup, toGroup, _)).map(_ => ())
          }

        case Left(error) => Future.successful(logger.error(error))
      }
    }

    private def syncAt(fromGroup: GroupIndex, toGroup: GroupIndex, height: Height): Future[Unit] = {
      blockFlowClient.getHashesAtHeight(fromGroup, toGroup, height).flatMap {
        case Right(hashesAtHeight) =>
          Future
            .traverse(hashesAtHeight.headers)(hash => syncWithHash(fromGroup, hash))
            .map(_ => ())
        case Left(error) => Future.successful(logger.error(error))
      }
    }

    private def syncWithHash(fromGroup: GroupIndex, hash: BlockEntry.Hash): Future[Unit] =
      blockFlowClient.getBlock(fromGroup, hash).flatMap {
        case Right(block) =>
          blockDao.insert(block).map(_ => ())
        case Left(error) => Future.successful(logger.error(error))
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
  }
}
