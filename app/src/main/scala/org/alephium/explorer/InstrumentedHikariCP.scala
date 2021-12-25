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

import java.sql.Driver

import scala.util.Try

import com.typesafe.config.Config
import com.zaxxer.hikari.metrics.prometheus.PrometheusMetricsTrackerFactory
import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.binder.db.PostgreSQLDatabaseMetrics
import io.micrometer.prometheus.{PrometheusConfig, PrometheusMeterRegistry}
import slick.jdbc.{JdbcDataSource, JdbcDataSourceFactory}
import slick.jdbc.hikaricp.HikariCPJdbcDataSource

object InstrumentedHikariCP extends JdbcDataSourceFactory {
  override def forConfig(c: Config,
                         driver: Driver,
                         name: String,
                         classLoader: ClassLoader): JdbcDataSource = {
    val hikariCPDataSource = HikariCPJdbcDataSource.forConfig(c, driver, name, classLoader)
    hikariCPDataSource.ds.setMetricsTrackerFactory(new PrometheusMetricsTrackerFactory())
    //enable metric only if the flag `enableDatabaseMetrics` enabled or missing
    if (Try(c.getBoolean("enableDatabaseMetrics")).getOrElse(true)) {
      val databaseName = c.getString("name")
      val pgMetrics    = new PostgreSQLDatabaseMetrics(hikariCPDataSource.ds, databaseName)
      val registry =
        new PrometheusMeterRegistry(PrometheusConfig.DEFAULT, Metrics.defaultRegistry, Clock.SYSTEM)
      pgMetrics.bindTo(registry)
    }
    hikariCPDataSource
  }
}
