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

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.{Duration => ScalaDuration, FiniteDuration}

import akka.http.scaladsl.model.Uri
import com.typesafe.scalalogging.StrictLogging
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import org.alephium.explorer.persistence.dao.UnconfirmedTxDao
import org.alephium.explorer.util.Scheduler

/*
 * Syncing mempool
 */

case object MempoolSyncService extends StrictLogging {

  def start(nodeUris: Seq[Uri], interval: FiniteDuration)(implicit ec: ExecutionContext,
                                                          dc: DatabaseConfig[PostgresProfile],
                                                          blockFlowClient: BlockFlowClient,
                                                          scheduler: Scheduler): Unit =
    scheduler.scheduleLoopAndForget(
      taskId        = this.productPrefix,
      firstInterval = ScalaDuration.Zero,
      loopInterval  = interval
    )(syncOnce(nodeUris))

  def syncOnce(nodeUris: Seq[Uri])(implicit ec: ExecutionContext,
                                   dc: DatabaseConfig[PostgresProfile],
                                   blockFlowClient: BlockFlowClient): Future[Unit] = {
    logger.debug("Syncing mempol")
    Future.sequence(nodeUris.map(syncMempool)).map { _ =>
      logger.debug("Mempool synced")
    }
  }

  private def syncMempool(uri: Uri)(implicit ec: ExecutionContext,
                                    dc: DatabaseConfig[PostgresProfile],
                                    blockFlowClient: BlockFlowClient): Future[Unit] = {
    blockFlowClient.fetchUnconfirmedTransactions(uri).flatMap {
      case Right(utxs) =>
        UnconfirmedTxDao.listHashes().flatMap { localUtxs =>
          val localUtxsSet = localUtxs.toSet
          val newHashes    = utxs.map(_.hash).toSet
          val newUtxs      = utxs.filterNot(tx => localUtxsSet.contains(tx.hash))
          val toDrop       = localUtxs.filterNot(tx => newHashes.contains(tx))
          UnconfirmedTxDao.removeAndInsertMany(toDrop, newUtxs)
        }
      case Left(error) =>
        logger.error(error)
        Future.successful(())
    }
  }
}
