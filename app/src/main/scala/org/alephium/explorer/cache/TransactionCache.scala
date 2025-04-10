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
