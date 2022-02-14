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

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import slick.sql.SqlAction

import org.alephium.explorer.CommonAsserts.isPreparedStatement

trait TestDBRunner {

  val config: DatabaseConfig[JdbcProfile]

  import config.profile.api._

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  def run[R, S <: NoStream, E <: Effect](action: SqlAction[R, S, E])(
      implicit executionContext: ExecutionContext): Future[R] = {
    config.db.run(action).recover {
      case error =>
        throw new RuntimeException(error)
    } flatMap { result =>
      //assert that this query is a prepared statement
      isPreparedStatement(action, config) match {
        case Failure(exception) =>
          Future.failed(exception)

        case Success(_) =>
          Future.successful(result)
      }
    }
  }
}
