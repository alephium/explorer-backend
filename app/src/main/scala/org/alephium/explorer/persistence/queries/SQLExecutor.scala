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
package org.alephium.explorer.persistence.queries

import java.sql.{PreparedStatement, ResultSet}

import scala.collection.Factory
import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try, Using}

import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

/**
  * Provides APIs for executing raw SQL queries while being
  * target database independent.
  *
  * For example: To support DynamoDB we would implement a `DynamoDBSQLExecutor`
  * similar to [[SQLExecutor.SlickSQLExecutor]]
  */
sealed trait SQLExecutor extends AutoCloseable {

  /** Execution context used for concurrent queries. This is configured by Slick */
  def ioExecutionContext: ExecutionContext

  /** Executes an update SQL */
  def runUpdate(sql: String): Try[Int]

  /**
    * Executes a select SQL query and parses SQL rows to typed rows.
    *
    * @param sql       Select sql only
    * @param rowParser Parser to convert each [[ResultSet]] to type [[T]]
    * @param factory   Target collection to store the results in.
    * @tparam T        Type of data
    * @tparam C        Type of target `Collection` to parse data into
    * @return          A collection of type [[C]] with data of type [[T]].
    */
  def runSelect[T, C[_]](sql: String)(rowParser: ResultSet => T)(
      implicit factory: Factory[T, C[T]]): Try[C[T]]

  /**
    * Executes count queries. Checks that the SQL result contains only one row.
    * @param sql   Count query to execute
    * @param parse Maps the row to count.
    */
  def runCount(sql: String)(parse: ResultSet => Int): Try[Int] =
    runSelect[Int, ListBuffer](sql)(parse) flatMap { buffer =>
      if (buffer.isEmpty) {
        Success(0)
      } else if (buffer.length == 1) {
        Success(buffer(0))
      } else {
        Failure(
          new Exception(s"Invalid row count for count query. Expected 1. Actual ${buffer.length}")
        )
      }
    }

}

object SQLExecutor {

  /**
    * Raw SQL executor for Slick
    */
  final class SlickSQLExecutor(config: DatabaseConfig[JdbcProfile]) extends SQLExecutor {
    override def ioExecutionContext: ExecutionContext =
      config.db.ioExecutionContext

    @SuppressWarnings(Array("org.wartremover.warts.While"))
    override def runSelect[T, C[_]](sql: String)(rowParser: ResultSet => T)(
        implicit factory: Factory[T, C[T]]): Try[C[T]] =
      Using.Manager { manager =>
        val session                      = manager(config.db.createSession())
        val statement: PreparedStatement = manager(session.prepareStatement(sql))
        val resultSet                    = manager(statement.executeQuery())

        val builder = factory.newBuilder

        while (resultSet.next()) {
          val _ = builder addOne rowParser(resultSet)
        }

        builder.result()
      }

    override def runUpdate(sql: String): Try[Int] =
      Using.Manager { manager =>
        val session   = manager(config.db.createSession())
        val statement = manager(session.prepareStatement(sql))
        statement.executeUpdate()
      }

    override def close(): Unit =
      config.db.close()
  }
}
