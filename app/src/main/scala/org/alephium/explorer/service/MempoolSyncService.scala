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

import akka.http.scaladsl.model.Uri
import com.typesafe.scalalogging.StrictLogging
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import org.alephium.explorer.persistence.dao.UnconfirmedTxDao
import org.alephium.util.Duration

/*
 * Syncing mempool
 */

trait MempoolSyncService extends SyncService.BlockFlow

object MempoolSyncService {
  def apply(syncPeriod: Duration, blockFlowClient: BlockFlowClient, utxDao: UnconfirmedTxDao)(
      implicit executionContext: ExecutionContext,
      databaseConfig: DatabaseConfig[PostgresProfile]): MempoolSyncService =
    new Impl(syncPeriod, blockFlowClient, utxDao)

  private class Impl(val syncPeriod: Duration,
                     blockFlowClient: BlockFlowClient,
                     utxDao: UnconfirmedTxDao)(implicit val executionContext: ExecutionContext,
                                               databaseConfig: DatabaseConfig[PostgresProfile])
      extends MempoolSyncService
      with StrictLogging {

    override def syncOnce(): Future[Unit] = {
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
