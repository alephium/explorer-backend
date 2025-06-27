// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.cache

import scala.collection.immutable.ArraySeq
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

import org.alephium.explorer.persistence.Database
import org.alephium.explorer.persistence.DBRunner._
import org.alephium.explorer.persistence.model.AppState._
import org.alephium.explorer.persistence.queries.{AppStateQueries, TransactionQueries}
import org.alephium.explorer.persistence.schema.CustomGetResult._
import org.alephium.protocol.ALPH
import org.alephium.util.Service

object TransactionCache {

  @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
  def apply(database: Database, reloadAfter: FiniteDuration = 10.minutes)(implicit
      ec: ExecutionContext
  ): TransactionCache =
    new TransactionCache(database, reloadAfter)
}

/** Transaction related cache
  *
  * @param mainChainTxnCount
  *   Stores current total number of `main_chain` transaction.
  */
class TransactionCache(database: Database, reloadAfter: FiniteDuration)(implicit
    val executionContext: ExecutionContext
) extends Service {

  private val mainChainTxnCount: AsyncReloadingCache[Int] = {
    AsyncReloadingCache(0, reloadAfter) { _ =>
      run(
        for {
          finalizedTime    <- AppStateQueries.get(LastFinalizedInputTime).map(_.map(_.time))
          finalizedTxCount <- AppStateQueries.get(FinalizedTxCount).map(_.map(_.count).getOrElse(0))
          nonFinalizedTxCount <- TransactionQueries.countTxsAfter(
            finalizedTime.getOrElse(ALPH.GenesisTimestamp)
          )
        } yield {
          finalizedTxCount + nonFinalizedTxCount
        }
      )(database.databaseConfig)
    }
  }

  def getMainChainTxnCount(): Int =
    mainChainTxnCount.get()

  override def startSelfOnce(): Future[Unit] = {
    Future.successful(mainChainTxnCount.expire())
  }

  override def stopSelfOnce(): Future[Unit] = {
    Future.unit
  }

  override def subServices: ArraySeq[Service] = ArraySeq(database)
}
