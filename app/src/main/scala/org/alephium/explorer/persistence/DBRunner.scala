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

import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._

trait DBRunner {
  def databaseConfig: DatabaseConfig[PostgresProfile]

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  def run[R, E <: Effect](action: DBAction[R, E])(
      implicit executionContext: ExecutionContext): Future[R] =
    databaseConfig.db.run(action).recover {
      case error => throw new RuntimeException(error)
    }
}

object DBRunner {
  @inline def apply(dc: DatabaseConfig[PostgresProfile]): DBRunner =
    new DBRunner {
      override def databaseConfig = dc
    }

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  def run[R, E <: Effect](databaseConfig: DatabaseConfig[PostgresProfile])(action: DBAction[R, E])(
      implicit executionContext: ExecutionContext): Future[R] =
    databaseConfig.db.run(action).recover {
      case error => throw new RuntimeException(error)
    }

  /** Temporary function until all things are made stateless */
  @SuppressWarnings(Array("org.wartremover.warts.Overloading"))
  def run[R, E <: Effect](action: DBAction[R, E])(
      implicit executionContext: ExecutionContext,
      databaseConfig: DatabaseConfig[PostgresProfile]): Future[R] =
    databaseConfig.db.run(action).recoverWith {
      case error => Future.failed(new RuntimeException(error))
    }

}
