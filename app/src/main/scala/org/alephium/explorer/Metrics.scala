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

import scala.concurrent.Future

import io.prometheus.metrics.model.registry.PrometheusRegistry
import io.vertx.ext.web._
import sttp.monad.MonadError
import sttp.tapir._
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.metrics.MetricLabels
import sttp.tapir.server.metrics.prometheus.PrometheusMetrics
import sttp.tapir.server.metrics.prometheus.PrometheusMetrics._
import sttp.tapir.server.vertx.VertxFutureServerInterpreter

object Metrics {
  val defaultRegistry: PrometheusRegistry = PrometheusRegistry.defaultRegistry
  val namespace                           = "explorer_backend"
  val prometheus: PrometheusMetrics[Future] = PrometheusMetrics[Future](
    namespace = namespace,
    registry = defaultRegistry,
    metrics = List(
      PrometheusMetrics.requestTotal(defaultRegistry, namespace, MetricLabels.Default),
      PrometheusMetrics.requestDuration(defaultRegistry, namespace, MetricLabels.Default),
      PrometheusMetrics.requestActive(defaultRegistry, namespace, MetricLabels.Default)
    )
  )

  /** Endpoint to expose the metrics
    *
    * @param callback
    *   a block of code to execute before serving the metrics, for example to reload
    */
  def endpoint(callback: => Unit): Router => Route = VertxFutureServerInterpreter().route(
    ServerEndpoint.public(
      sttp.tapir.endpoint.get.in(prometheus.endpointPrefix).out(plainBody[PrometheusRegistry]),
      (monad: MonadError[Future]) =>
        (_: Unit) => {
          callback
          monad.eval(Right(defaultRegistry): Either[Unit, PrometheusRegistry])
        }
    )
  )
}
