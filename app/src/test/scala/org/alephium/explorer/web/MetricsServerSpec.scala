// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.web

import scala.concurrent.duration._

import org.alephium.explorer._
import org.alephium.explorer.cache.MetricCache
import org.alephium.explorer.config.BootMode
import org.alephium.explorer.persistence.{Database, DatabaseFixtureForAll}

class MetricsServerSpec
    extends AlephiumFutureSpec
    with DatabaseFixtureForAll
    with HttpServerFixture {

  private val metricCache = new MetricCache(new Database(BootMode.ReadWrite), 1.second)(
    executionContext
  ) {
    override def getFungibleCount(): Int = 7
    override def getNFTCount(): Int      = 8
    override def getEventCount(): Int    = 9
  }
  private val server = new MetricsServer(metricCache)

  override val routes = server.routes

  "metrics" should {
    "reload the cache and expose the updated gauges" in {
      val response = Get("/metrics")
      val body = response.body match {
        case Right(text) => text
        case Left(error) => fail(error)
      }

      body should include("alephimum_explorer_backend_fungible_count")
      body should include("alephimum_explorer_backend_nft_count")
      body should include("alephimum_explorer_backend_event_count")
      body should include("7.0")
      body should include("8.0")
      body should include("9.0")
    }
  }
}
