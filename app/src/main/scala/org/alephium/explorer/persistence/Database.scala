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

package org.alephium.explorer.persistence

import scala.collection.immutable.ArraySeq
import scala.concurrent.{ExecutionContext, Future}
import scala.util._

import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import org.alephium.explorer.config.BootMode
import org.alephium.explorer.persistence.dao.HealthCheckDao
import org.alephium.explorer.util.FutureUtil._
import org.alephium.util.Service

class Database(bootMode: BootMode)(implicit val executionContext: ExecutionContext,
                                   val databaseConfig: DatabaseConfig[PostgresProfile])
    extends Service {

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
