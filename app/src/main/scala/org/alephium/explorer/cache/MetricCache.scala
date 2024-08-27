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

import io.prometheus.metrics.core.metrics.Gauge

import org.alephium.explorer.persistence.Database
import org.alephium.explorer.persistence.DBRunner._
import org.alephium.explorer.persistence.queries.{EventQueries, TokenQueries}
import org.alephium.util.Service

class MetricCache(database: Database, reloadPeriod: FiniteDuration)(implicit
    val executionContext: ExecutionContext
) extends Service {

  private val fungibleCount: AsyncReloadingCache[Int] = {
    AsyncReloadingCache(0, reloadPeriod) { _ =>
      run(TokenQueries.countFungibles())(database.databaseConfig).map { count =>
        MetricCache.fungibleCountGauge.set(count.toDouble)
        count
      }
    }
  }

  private val nftCount: AsyncReloadingCache[Int] = {
    AsyncReloadingCache(0, reloadPeriod) { _ =>
      run(TokenQueries.countNFT())(database.databaseConfig).map { count =>
        MetricCache.nftCountGauge.set(count.toDouble)
        count
      }
    }
  }

  private val eventCount: AsyncReloadingCache[Int] = {
    AsyncReloadingCache(0, reloadPeriod) { _ =>
      run(EventQueries.countEvents())(database.databaseConfig).map { count =>
        MetricCache.eventCountGauge.set(count.toDouble)
        count
      }
    }
  }

  def reloadTokenCountIfOverdue(): Unit = {
    val _ = fungibleCount.get()
    val _ = nftCount.get()
  }

  def reloadEventCountIfOverdue(): Unit = {
    val _ = eventCount.get()
  }

  override def startSelfOnce(): Future[Unit] = {
    for {
      _ <- fungibleCount.expireAndReloadFuture().map(_ => ())
      _ <- nftCount.expireAndReloadFuture().map(_ => ())
      _ <- eventCount.expireAndReloadFuture().map(_ => ())
    } yield ()
  }

  override def stopSelfOnce(): Future[Unit] = {
    Future.unit
  }

  override def subServices: ArraySeq[Service] = ArraySeq(database)
}
object MetricCache {
  val fungibleCountGauge: Gauge = Gauge
    .builder()
    .name(
      "alephimum_explorer_backend_fungible_count"
    )
    .help(
      "Number of fungible tokens in the system"
    )
    .register()

  val nftCountGauge: Gauge = Gauge
    .builder()
    .name(
      "alephimum_explorer_backend_nft_count"
    )
    .help(
      "Number of NFT in the system"
    )
    .register()

  val eventCountGauge: Gauge = Gauge
    .builder()
    .name(
      "alephimum_explorer_backend_event_count"
    )
    .help(
      "Number of events in the system"
    )
    .register()

}
