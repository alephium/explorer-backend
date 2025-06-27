// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence

import scala.collection.immutable.ArraySeq
import scala.concurrent.{ExecutionContext, Future}
import scala.util._

import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import org.alephium.explorer.config.{BootMode, ExplorerConfig}
import org.alephium.explorer.persistence.dao.HealthCheckDao
import org.alephium.explorer.util.FutureUtil._
import org.alephium.util.Service

class Database(bootMode: BootMode)(implicit
    val executionContext: ExecutionContext,
    val databaseConfig: DatabaseConfig[PostgresProfile],
    explorerConfig: ExplorerConfig
) extends Service {

  override def startSelfOnce(): Future[Unit] =
    bootMode match {
      case BootMode.ReadOnly =>
        HealthCheckDao.healthCheck().mapSyncToUnit()

      case BootMode.ReadWrite | BootMode.WriteOnly =>
        DBInitializer.initialize().mapSyncToUnit()
    }

  override def stopSelfOnce(): Future[Unit] = {
    Future.fromTry(Try(databaseConfig.db.close()))
  }

  override def subServices: ArraySeq[Service] = ArraySeq.empty
}
