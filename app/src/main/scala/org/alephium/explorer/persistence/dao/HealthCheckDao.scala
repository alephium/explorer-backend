// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.dao

import scala.concurrent.{ExecutionContext, Future}

import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile
import slick.jdbc.meta.MTable

import org.alephium.explorer.persistence.DBRunner._

object HealthCheckDao {

  def healthCheck()(implicit
      executionContext: ExecutionContext,
      databaseConfig: DatabaseConfig[PostgresProfile]
  ): Future[Unit] =
    run(MTable.getTables).map(_ => ())
}
