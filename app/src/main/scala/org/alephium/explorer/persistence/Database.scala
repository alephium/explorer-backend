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

import scala.concurrent.{ExecutionContext, Future}
import java.time.{OffsetTime, ZonedDateTime}
import java.util.{Timer, TimerTask}

import slick.basic.DatabaseConfig
import scala.annotation.tailrec
import scala.concurrent._
import scala.concurrent.duration._
import scala.util.{Failure, Success}

import com.typesafe.scalalogging.StrictLogging

import org.alephium.explorer._
import org.alephium.explorer.util.Scheduler.scheduleTime
import org.alephium.util.Service


class Database(val databaseConfig:  DatabaseConfig[PostgresProfile])(implicit ec:ExecutionContext) extends Service {

  override def start():Future[Unit] = {
      if (readOnly) {
        HealthCheckDao.healthCheck().mapSyncToUnit
      } else {
        DBInitializer.initialize().mapSyncToUnit
      }
  }
}
