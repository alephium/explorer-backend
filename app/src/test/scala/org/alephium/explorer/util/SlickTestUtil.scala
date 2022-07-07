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
package org.alephium.explorer.util

import scala.concurrent.ExecutionContext

import org.scalatest.matchers.should.Matchers._
import slick.dbio.Effect
import slick.jdbc.PostgresProfile.api._
import slick.jdbc.SQLActionBuilder
import slick.sql.{FixedSqlStreamingAction, SqlStreamingAction}

import org.alephium.explorer.persistence.DBActionR

object SlickTestUtil {

  /** For SQL queries */
  implicit class SQLActionBuilderImplicits(sql: SQLActionBuilder) {

    /** Adds `EXPLAIN ANALYZE` to head query */
    def explainAnalyze(): SqlStreamingAction[Vector[String], String, Effect.Read] =
      alterHeadQuery(sql, "EXPLAIN ANALYZE")

    /** Adds `EXPLAIN` to head query */
    def explain(): SqlStreamingAction[Vector[String], String, Effect.Read] =
      alterHeadQuery(sql, "EXPLAIN")
  }

  /** For typed static queries  */
  implicit class FixedSqlStreamingActionImplicits[+R, +T, -E <: Effect](
      sql: FixedSqlStreamingAction[R, T, E]) {

    /** Adds `EXPLAIN ANALYZE` to head query */
    def explainAnalyze(): SqlStreamingAction[Vector[String], String, Effect.Read] =
      alterHeadQuery(sql, "EXPLAIN ANALYZE")

    /** Adds `EXPLAIN` to head query */
    def explain(): SqlStreamingAction[Vector[String], String, Effect.Read] =
      alterHeadQuery(sql, "EXPLAIN")

    def explainFlatten()(implicit ec: ExecutionContext): DBActionR[String] =
      alterHeadQuery(sql, "EXPLAIN").map(_.mkString("\n"))
  }

  /** Alter's first query with the prefix. */
  def alterHeadQuery(sql: SQLActionBuilder,
                     prefix: String): SqlStreamingAction[Vector[String], String, Effect.Read] =
    sql
      .copy(queryParts = sql.queryParts.updated(0, s"$prefix ${sql.queryParts.head}"))
      .as[String]

  /** Alter's first query with the prefix. */
  def alterHeadQuery[R, T, E <: Effect](
      sql: FixedSqlStreamingAction[R, T, E],
      prefix: String): SqlStreamingAction[Vector[String], String, Effect.Read] =
    if (sql.statements.sizeIs == 1) {
      alterHeadQuery(sql"#${sql.statements.head}", prefix)
    } else if (sql.statements.sizeIs <= 0) {
      fail(s"$prefix cannot be implemented for empty SQL. Statement size: ${sql.statements.size}")
    } else {
      fail(
        s"TODO: $prefix not yet implemented for nested queries. Statement size: ${sql.statements.size}"
      )
    }

}
