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

package org.alephium.explorer

import scala.concurrent.{ExecutionContext, Future}

import akka.http.scaladsl.server.Route
import io.prometheus.client.CollectorRegistry
import sttp.tapir.server.akkahttp.AkkaHttpServerInterpreter
import sttp.tapir.server.metrics.MetricLabels
import sttp.tapir.server.metrics.prometheus.PrometheusMetrics

object Metrics {
  val defaultRegistry: CollectorRegistry = CollectorRegistry.defaultRegistry
  val namespace                          = "explorer_backend"
  val prometheus: PrometheusMetrics[Future] = PrometheusMetrics[Future](
    namespace = namespace,
    registry  = defaultRegistry,
    metrics = List(
      PrometheusMetrics.requestTotal(defaultRegistry, namespace, MetricLabels.Default),
      PrometheusMetrics.requestDuration(defaultRegistry, namespace, MetricLabels.Default),
      PrometheusMetrics.requestActive(defaultRegistry, namespace, MetricLabels.Default)
    )
  )

  def route(implicit ec: ExecutionContext): Route =
    AkkaHttpServerInterpreter().toRoute(prometheus.metricsEndpoint)
}
