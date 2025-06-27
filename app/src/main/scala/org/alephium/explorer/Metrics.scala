// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer

import scala.concurrent.Future

import io.prometheus.metrics.model.registry.PrometheusRegistry
import sttp.tapir.server.metrics.MetricLabels
import sttp.tapir.server.metrics.prometheus.PrometheusMetrics

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
}
