package org.alephium.explorer.service

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success}

import com.typesafe.scalalogging.StrictLogging

import org.alephium.explorer.{sideEffect, AnyOps, Hash}
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

    private val chainIndexes: Seq[(Int, Int)] = for {
      i <- 0 to groupNum - 1
      j <- 0 to groupNum - 1
    } yield (i, j)

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

    private def syncChain(fromGroup: Int, toGroup: Int): Future[Unit] = {
      blockFlowClient.getChainInfo(fromGroup, toGroup).flatMap {
        case Right(nodeHeight) =>
          blockDao.maxHeight(fromGroup, toGroup).flatMap { maybeLocalHeight =>
            logger.debug(
              s"Syncing ($fromGroup, $toGroup). Heights: local = $maybeLocalHeight, node = $nodeHeight")
            val heights = generateHeights(maybeLocalHeight, nodeHeight.currentHeight)
            Future.traverse(heights)(syncAt(fromGroup, toGroup, _)).map(_ => ())
          }

        case Left(error) => Future.successful(logger.error(error))
      }
    }

    private def syncAt(fromGroup: Int, toGroup: Int, height: Int): Future[Unit] = {
      blockFlowClient.getHashesAtHeight(fromGroup, toGroup, height).flatMap {
        case Right(hashesAtHeight) =>
          Future.traverse(hashesAtHeight.headers)(syncWithHash).map(_ => ())
        case Left(error) => Future.successful(logger.error(error))
      }
    }

    private def syncWithHash(hash: Hash): Future[Unit] =
      blockFlowClient.getBlock(hash).flatMap {
        case Right(block) =>
          blockDao.insert(block).map(_.left.map(logger.error(_))).map(_ => ())
        case Left(error) => Future.successful(logger.error(error))
      }

    private def generateHeights(maybeLocal: Option[Int], remote: Int): Seq[Int] =
      maybeLocal match {
        case Some(local) =>
          if (local === remote) {
            Seq.empty
          } else {
            ((local + 1) to remote).toSeq
          }
        case None =>
          (0 to remote).toSeq
      }
  }
}
