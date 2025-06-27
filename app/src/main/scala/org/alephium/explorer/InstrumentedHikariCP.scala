// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

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
