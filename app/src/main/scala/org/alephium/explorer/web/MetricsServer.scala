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

package org.alephium.explorer.web

import scala.collection.immutable.ArraySeq
import scala.concurrent.ExecutionContext

import io.prometheus.metrics.core.metrics.Gauge
import io.vertx.ext.web._

import org.alephium.explorer.Metrics
import org.alephium.explorer.cache.MetricCache

class MetricsServer(cache: MetricCache)(implicit
    val executionContext: ExecutionContext
) extends Server {

  val routes: ArraySeq[Router => Route] =
    ArraySeq(
      Metrics.endpoint(reloadMetrics())
    )

  def reloadMetrics(): Unit = {
    MetricServer.fungibleCountGauge.set(cache.getFungibleCount().toDouble)
    MetricServer.nftCountGauge.set(cache.getNFTCount().toDouble)
    MetricServer.eventCountGauge.set(cache.getEventCount().toDouble)
  }
}

object MetricServer {
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
