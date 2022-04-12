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

object DBRunner_V2 {

  def apply(databaseConfig: DatabaseConfig[PostgresProfile]): DBRunner_V2 =
    new DBRunner_V2(databaseConfig)(databaseConfig.db.ioExecutionContext)

  @inline def runAction[R, E <: Effect](action: DBAction[R, E])(
      implicit runner: DBRunner_V2): Future[R] =
    runner.run(action)

}

/**
  * Provides ability to execute database side-effects/IO.
  *
  * To create an instance use companion object's apply.
  *
  * @note Make sure to invoke [[close()]] to release database connection.
  *
  * @param databaseConfig Slick's database configuration
  * @param ec             Database level [[ExecutionContext]] used to convert DB exceptions
  *                       to [[RuntimeException]].
  *
  */
class DBRunner_V2 private (databaseConfig: DatabaseConfig[PostgresProfile])(
    implicit ec: ExecutionContext)
    extends AutoCloseable {

  def run[R, E <: Effect](action: DBAction[R, E]): Future[R] =
    databaseConfig.db.run(action) recoverWith {
      case error =>
        //I'm not sure why we do this?
        Future.failed(new RuntimeException(error))
    }

  override def close(): Unit =
    databaseConfig.db.close()
}
