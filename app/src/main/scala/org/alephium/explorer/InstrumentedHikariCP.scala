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

import com.typesafe.config.Config
import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.binder.db.PostgreSQLDatabaseMetrics
import io.micrometer.prometheusmetrics.{PrometheusConfig, PrometheusMeterRegistry}
import slick.jdbc.{JdbcDataSource, JdbcDataSourceFactory}
import slick.jdbc.hikaricp.HikariCPJdbcDataSource

object InstrumentedHikariCP extends JdbcDataSourceFactory {
  override def forConfig(
      config: Config,
      driver: Driver,
      name: String,
      classLoader: ClassLoader
  ): JdbcDataSource = {
    val registry =
      new PrometheusMeterRegistry(PrometheusConfig.DEFAULT, Metrics.defaultRegistry, Clock.SYSTEM)
    val hikariCPDataSource = HikariCPJdbcDataSource.forConfig(config, driver, name, classLoader)
    val databaseName       = config.getString("name")
    val pgMetrics          = new PostgreSQLDatabaseMetrics(hikariCPDataSource.ds, databaseName)

    hikariCPDataSource.ds.setMetricRegistry(registry)
    pgMetrics.bindTo(registry)

    hikariCPDataSource
  }
}
