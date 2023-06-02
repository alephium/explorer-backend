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

import java.sql.PreparedStatement

import scala.collection.immutable.ArraySeq
import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag

import slick.dbio.DBIO
import slick.jdbc._
import slick.jdbc.PostgresProfile.api._
import slick.sql._

import org.alephium.explorer.api.model.Pagination
import org.alephium.explorer.persistence.{DBActionR, DBActionSR}

/** Convenience functions for Slick */
object SlickUtil {

  implicit class ResultEnrichment[A](val action: DBActionSR[A]) extends AnyVal {

    /** Fetch single row else fail.
      */
    @SuppressWarnings(Array("org.wartremover.warts.IterableOps"))
    def exactlyOne(implicit ec: ExecutionContext): DBActionR[A] =
      action.flatMap { rows =>
        rows.size match {
          case 1 => DBIO.successful(rows.head)
          case n => DBIO.failed(new RuntimeException(s"Expected 1 result, actual $n"))
        }
      }

    /** Expects query to return headOption
      */
    @SuppressWarnings(Array("org.wartremover.warts.IterableOps"))
    def headOrEmpty(implicit ec: ExecutionContext): DBActionR[ArraySeq[A]] =
      action.flatMap { rows =>
        rows.size match {
          case 0 => DBIO.successful(ArraySeq.empty)
          case _ => DBIO.successful(rows.take(1))
        }
      }

    @SuppressWarnings(Array("org.wartremover.warts.IterableOps"))
    def headOrNone(implicit ec: ExecutionContext): DBActionR[Option[A]] =
      action.flatMap { rows =>
        rows.size match {
          case 0 => DBIO.successful(None)
          case _ => DBIO.successful(rows.headOption)
        }
      }
  }

  implicit class OptionResultEnrichment[A](val action: DBActionSR[Option[A]]) extends AnyVal {

    /** Expects query to return less than or equal to 1. Fails is the result is greater than 1.
      *
      * This is used to ensure queries selecting on SQL functions like `sum` and `max` return only
      * one row or none. If the more than one rows are return then there is a problem in the query
      * itself.
      */
    @SuppressWarnings(Array("org.wartremover.warts.IterableOps"))
    def oneOrNone(implicit ec: ExecutionContext): DBActionR[Option[A]] =
      action.flatMap { rows =>
        rows.size match {
          case 0 => DBIO.successful(None)
          case 1 => DBIO.successful(rows.head)
          case n => DBIO.failed(new RuntimeException(s"Expected 1 result, actual $n"))
        }
      }
  }

  implicit class RichSqlActionBuilder[A](val action: SQLActionBuilder) extends AnyVal {
    @SuppressWarnings(
      Array(
        "org.wartremover.warts.AsInstanceOf",
        "org.wartremover.warts.IsInstanceOf",
        "org.wartremover.warts.IterableOps"
      )
    )
    def asAS[R: ClassTag](implicit
        rconv: GetResult[R]
    ): SqlStreamingAction[ArraySeq[R], R, Effect] = {
      val query =
        if (action.queryParts.lengthIs == 1 && action.queryParts(0).isInstanceOf[String]) {
          action.queryParts(0).asInstanceOf[String]
        } else {
          action.queryParts.iterator.map(String.valueOf).mkString
        }
      new StreamingInvokerAction[ArraySeq[R], R, Effect] {
        def statements = List(query)
        protected[this] def createInvoker(statements: Iterable[String]) = new StatementInvoker[R] {
          val getStatement = statements.head
          protected def setParam(st: PreparedStatement) =
            action.unitPConv((), new PositionedParameters(st))
          protected def extractValue(rs: PositionedResult): R = rconv(rs)
        }
        protected[this] def createBuilder = ArraySeq.newBuilder[R]
      }
    }

    @SuppressWarnings(
      Array(
        "org.wartremover.warts.AsInstanceOf",
        "org.wartremover.warts.IsInstanceOf",
        "org.wartremover.warts.IterableOps"
      )
    )
    def asASE[R: ClassTag](rconv: GetResult[R]): SqlStreamingAction[ArraySeq[R], R, Effect] = {
      val query =
        if (action.queryParts.lengthIs == 1 && action.queryParts(0).isInstanceOf[String]) {
          action.queryParts(0).asInstanceOf[String]
        } else {
          action.queryParts.iterator.map(String.valueOf).mkString
        }
      new StreamingInvokerAction[ArraySeq[R], R, Effect] {
        def statements = List(query)
        protected[this] def createInvoker(statements: Iterable[String]) = new StatementInvoker[R] {
          val getStatement = statements.head
          protected def setParam(st: PreparedStatement) =
            action.unitPConv((), new PositionedParameters(st))
          protected def extractValue(rs: PositionedResult): R = rconv(rs)
        }
        protected[this] def createBuilder = ArraySeq.newBuilder[R]
      }
    }

    def paginate(pagination: Pagination): SQLActionBuilder = {

      val parameters: SetParameter[Unit] =
        (_: Unit, params: PositionedParameters) => {
          action.unitPConv((), params)
          params >> pagination.limit
          params >> pagination.offset
        }

      action.copy(
        queryParts = action.queryParts :+ "LIMIT ? OFFSET ?",
        unitPConv = parameters
      )
    }
  }

  def paramPlaceholderTuple2(rows: Int, columns: Int): String =
    paramPlaceholder(rows, columns, "(?, ?)")

  @SuppressWarnings(Array("org.wartremover.warts.Overloading"))
  def paramPlaceholder(rows: Int, columns: Int): String =
    paramPlaceholder(rows, columns, "?")

  /** Builds placeholders for generating parameterised SQL queries.
    *
    * Example: If rows = 2, columns = 3 & placeHolder = "?" this returns comma separated rows (?, ?,
    * ?),(?, ?, ?).
    */
  @SuppressWarnings(Array("org.wartremover.warts.Overloading"))
  def paramPlaceholder(rows: Int, columns: Int, placeHolder: String): String =
    if (rows <= 0 || columns <= 0) {
      ""
    } else {
      val placeholders =
        Array
          .fill(columns)(placeHolder)
          .mkString("(", ", ", ")")

      Array
        .fill(rows)(placeholders)
        .mkString(",")
    }
}
