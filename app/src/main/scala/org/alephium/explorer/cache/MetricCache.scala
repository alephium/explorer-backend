// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.cache

import scala.collection.immutable.ArraySeq
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

import org.alephium.explorer.persistence.Database
import org.alephium.explorer.persistence.DBRunner._
import org.alephium.explorer.persistence.queries.{EventQueries, TokenQueries}
import org.alephium.util.Service

class MetricCache(database: Database, reloadPeriod: FiniteDuration)(implicit
    val executionContext: ExecutionContext
) extends Service {

  private val fungibleCount: AsyncReloadingCache[Int] = {
    AsyncReloadingCache(0, reloadPeriod) { _ =>
      run(TokenQueries.countFungibles())(database.databaseConfig)
    }
  }

  private val nftCount: AsyncReloadingCache[Int] = {
    AsyncReloadingCache(0, reloadPeriod) { _ =>
      run(TokenQueries.countNFT())(database.databaseConfig)
    }
  }

  private val eventCount: AsyncReloadingCache[Int] = {
    AsyncReloadingCache(0, reloadPeriod) { _ =>
      run(EventQueries.countEvents())(database.databaseConfig)
    }
  }

  def getFungibleCount(): Int = fungibleCount.get()
  def getNFTCount(): Int      = nftCount.get()
  def getEventCount(): Int    = eventCount.get()

  override def startSelfOnce(): Future[Unit] = {
    Future.successful {
      fungibleCount.expire()
      nftCount.expire()
      eventCount.expire()
    }
  }

  override def stopSelfOnce(): Future[Unit] = {
    Future.unit
  }

  override def subServices: ArraySeq[Service] = ArraySeq(database)
}
