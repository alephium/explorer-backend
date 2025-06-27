// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.service

import scala.collection.immutable.ArraySeq
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.{Duration => ScalaDuration, FiniteDuration}

import com.typesafe.scalalogging.StrictLogging
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile
import sttp.model.Uri

import org.alephium.explorer.persistence.dao.MempoolDao
import org.alephium.explorer.util.Scheduler

/*
 * Syncing mempool
 */

case object MempoolSyncService extends StrictLogging {

  def start(nodeUris: ArraySeq[Uri], interval: FiniteDuration)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      blockFlowClient: BlockFlowClient,
      scheduler: Scheduler
  ): Future[Unit] =
    scheduler.scheduleLoop(
      taskId = this.productPrefix,
      firstInterval = ScalaDuration.Zero,
      loopInterval = interval
    )(syncOnce(nodeUris))

  def syncOnce(nodeUris: ArraySeq[Uri])(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      blockFlowClient: BlockFlowClient
  ): Future[Unit] = {
    Future.sequence(nodeUris.map(syncMempool)).map(_ => ())
  }

  private def syncMempool(uri: Uri)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      blockFlowClient: BlockFlowClient
  ): Future[Unit] = {
    blockFlowClient.fetchMempoolTransactions(uri).flatMap { utxs =>
      MempoolDao.listHashes().flatMap { localUtxs =>
        val localUtxsSet = localUtxs.toSet
        val newHashes    = utxs.map(_.hash).toSet
        val newUtxs      = utxs.filterNot(tx => localUtxsSet.contains(tx.hash))
        val toDrop       = localUtxs.filterNot(tx => newHashes.contains(tx))
        MempoolDao.removeAndInsertMany(toDrop, newUtxs)
      }
    }
  }
}
