// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only
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
