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

import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.persistence.Database
import org.alephium.explorer.persistence.DBRunner._
import org.alephium.explorer.persistence.queries.TransactionQueries
import org.alephium.util.Service

object TransactionCache {

  @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
  def apply(
      database: Database,
      cacheTxCountReloadPeriod: FiniteDuration = 5.seconds,
      cacheAddressCountReloadPeriod: FiniteDuration = 5.minutes
  )(implicit
      ec: ExecutionContext
  ): TransactionCache =
    new TransactionCache(database, cacheTxCountReloadPeriod, cacheAddressCountReloadPeriod)
}

/** Transaction related cache
  *
  * @param mainChainTxnCount
  *   Stores current total number of `main_chain` transaction.
  */
class TransactionCache(
    database: Database,
    cacheTxCountReloadPeriod: FiniteDuration,
    cacheAddressCountReloadPeriod: FiniteDuration
)(implicit
    val executionContext: ExecutionContext
) extends Service {

  private val mainChainTxnCount: AsyncReloadingCache[Int] = {
    AsyncReloadingCache(0, cacheTxCountReloadPeriod) { _ =>
      run(TransactionQueries.mainTransactions.length.result)(database.databaseConfig)
    }
  }

  private val addressCount: AsyncReloadingCache[Int] = {
    AsyncReloadingCache(0, cacheAddressCountReloadPeriod) { _ =>
      run(TransactionQueries.numberOfActiveAddressesQuery())(database.databaseConfig)
    }
  }

  def getMainChainTxnCount(): Int =
    mainChainTxnCount.get()

  def getAddressCount(): Int =
    addressCount.get()

  override def startSelfOnce(): Future[Unit] = {
    for {
      _ <- mainChainTxnCount.expireAndReloadFuture()
      _ <- addressCount.expireAndReloadFuture()
    } yield ()
  }

  override def stopSelfOnce(): Future[Unit] = {
    Future.unit
  }

  override def subServices: ArraySeq[Service] = ArraySeq(database)
}
