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

import slick.dbio.Effect
import slick.jdbc.PostgresProfile.api._
import slick.jdbc.SQLActionBuilder
import slick.sql.{FixedSqlStreamingAction, SqlStreamingAction}

import org.alephium.explorer.persistence.DBActionR

object SlickExplainUtil {

  /** For SQL queries */
  implicit class SQLActionBuilderImplicits(sql: SQLActionBuilder) {

    /** Adds `EXPLAIN ANALYZE` to head query */
    def explainAnalyze(): SqlStreamingAction[Vector[String], String, Effect.Read] =
      alterHeadQuery(sql, "EXPLAIN ANALYZE")

    /** Adds `EXPLAIN` to head query */
    def explain(): SqlStreamingAction[Vector[String], String, Effect.Read] =
      alterHeadQuery(sql, "EXPLAIN")
  }

  /** For typed static queries */
  implicit class FixedSqlStreamingActionImplicits[+R, +T, -E <: Effect](
      sql: FixedSqlStreamingAction[R, T, E]
  ) {

    /** Adds `EXPLAIN ANALYZE` to head query */
    def explainAnalyze(): SqlStreamingAction[Vector[String], String, Effect.Read] =
      alterHeadQuery(sql, "EXPLAIN ANALYZE")

    def explainAnalyzeFlatten()(implicit ec: ExecutionContext): DBActionR[String] =
      explainAnalyze().map(_.mkString("\n"))

    /** Adds `EXPLAIN` to head query */
    def explain(): SqlStreamingAction[Vector[String], String, Effect.Read] =
      alterHeadQuery(sql, "EXPLAIN")

    def explainFlatten()(implicit ec: ExecutionContext): DBActionR[String] =
      explain().map(_.mkString("\n"))
  }

  /** Alter's first query with the prefix. */
  @SuppressWarnings(Array("org.wartremover.warts.Overloading", "org.wartremover.warts.IterableOps"))
  def alterHeadQuery(
      sql: SQLActionBuilder,
      prefix: String
  ): SqlStreamingAction[Vector[String], String, Effect.Read] =
    sql
      .copy(sql = s"$prefix ${sql.sql}")
      .as[String]

  /** Alter's first query with the prefix. */
  @SuppressWarnings(Array("org.wartremover.warts.Overloading", "org.wartremover.warts.IterableOps"))
  def alterHeadQuery[R, T, E <: Effect](
      sql: FixedSqlStreamingAction[R, T, E],
      prefix: String
  ): SqlStreamingAction[Vector[String], String, Effect.Read] =
    if (sql.statements.sizeIs == 1) {
      alterHeadQuery(sql"#${sql.statements.head}", prefix)
    } else if (sql.statements.sizeIs <= 0) {
      throw new Exception(
        s"$prefix cannot be implemented for empty SQL. Statement size: ${sql.statements.size}"
      )
    } else {
      throw new Exception(
        s"TODO: $prefix not yet implemented for nested queries. Statement size: ${sql.statements.size}"
      )
    }

}
