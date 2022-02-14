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

import scala.util.{Failure, Success, Try, Using}

import org.scalatest.matchers.should.Matchers._
import slick.basic.DatabaseConfig
import slick.dbio.{Effect, NoStream}
import slick.jdbc.JdbcProfile
import slick.sql.SqlAction

/**
  * Common assertions functions for all test-cases.
  * */
object CommonAsserts {

  /**
    * Checks if the query is a prepared statement.
    *
    * Executes the test described
    * <a href="https://jdbc.postgresql.org/documentation/head/server-prepare.html#server-prepared-statement-example">here</a>.
    *
    * @note This check is only valid when `prepareThreshold` has reached
    *       the maximum configured `threshold`.
    */
  def isPreparedStatement[R, S <: NoStream, E <: Effect](
      action: SqlAction[R, S, E],
      config: DatabaseConfig[JdbcProfile]): Try[Unit] =
    Using.Manager { manager =>
      //cannot run an empty query
      action.statements should not be empty

      val session = manager(config.db.createSession())

      //assert all statements are prepared
      action.statements foreach { statement =>
        val prepStatement = manager(session.prepareStatement(statement))
        if (!prepStatement.unwrap(classOf[org.postgresql.PGStatement]).isUseServerPrepare) {
          throw new AssertionError(s"Not prepared statement: $statement")
        }
      }
    }

  /**
    * Asserts the query is a prepared statement.
    */
  def assertIsPreparedStatement[R, S <: NoStream, E <: Effect](
      action: SqlAction[R, S, E],
      config: DatabaseConfig[JdbcProfile]): Unit =
    isPreparedStatement(action, config) match {
      case Failure(exception) =>
        throw exception //assertion failed

      case Success(_) =>
        ()
    }
}
