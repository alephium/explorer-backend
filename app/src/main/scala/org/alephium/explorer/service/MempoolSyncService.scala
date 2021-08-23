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

import akka.http.scaladsl.model.Uri
import com.typesafe.scalalogging.StrictLogging

import org.alephium.explorer.persistence.dao.UnconfirmedTxDao
import org.alephium.explorer.sideEffect
import org.alephium.util.Duration

/*
 * Syncing mempool
 */

trait MempoolSyncService {
  def start(newUris: Seq[Uri]): Future[Unit]
  def stop(): Future[Unit]
}

object MempoolSyncService {
  def apply(syncPeriod: Duration, blockFlowClient: BlockFlowClient, utxDao: UnconfirmedTxDao)(
      implicit executionContext: ExecutionContext): MempoolSyncService =
    new Impl(syncPeriod, blockFlowClient, utxDao)

  private class Impl(syncPeriod: Duration,
                     blockFlowClient: BlockFlowClient,
                     utxDao: UnconfirmedTxDao)(implicit executionContext: ExecutionContext)
      extends MempoolSyncService
      with StrictLogging {

    private val stopped: Promise[Unit]  = Promise()
    private val syncDone: Promise[Unit] = Promise()

    private var startedOnce = false

    var nodeUris: Seq[Uri] = Seq.empty

    def start(newUris: Seq[Uri]): Future[Unit] = {
      Future.successful {
        startedOnce = true
        nodeUris    = newUris
        sync()
      }
    }

    def stop(): Future[Unit] = {
      if (!startedOnce) {
        syncDone.failure(new IllegalStateException).future
      } else {
        sideEffect(if (!stopped.isCompleted) stopped.success(()))
        syncDone.future
      }
    }

    @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
    private def sync(): Unit = {
      syncOnce().onComplete {
        case Success(_) =>
          continue()
        case Failure(e) =>
          logger.error("Failure while syncing", e)
          continue()
      }
      def continue() = {
        if (stopped.isCompleted) {
          syncDone.success(())
        } else {
          Thread.sleep(syncPeriod.millis)
          sync()
        }
      }
    }

    private def syncOnce(): Future[Unit] = {
      logger.debug("Syncing mempol")
      Future.sequence(nodeUris.map(syncMempool)).map { _ =>
        logger.debug("Mempool synced")
      }
    }

    private def syncMempool(
        uri: Uri
    ): Future[Unit] = {
      blockFlowClient.fetchUnconfirmedTransactions(uri).flatMap {
        case Right(utxs) =>
          utxDao.listHashes().flatMap { localUtxs =>
            val localUtxsSet = localUtxs.toSet
            val newHashes    = utxs.map(_.hash).toSet
            val newUtxs      = utxs.filterNot(tx => localUtxsSet.contains(tx.hash))
            val toDrop       = localUtxs.filterNot(tx => newHashes.contains(tx))
            utxDao.removeAndInsertMany(toDrop, newUtxs)
          }
        case Left(error) =>
          logger.error(error)
          Future.successful(())
      }
    }
  }
}
