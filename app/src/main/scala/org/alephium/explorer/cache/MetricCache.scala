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
