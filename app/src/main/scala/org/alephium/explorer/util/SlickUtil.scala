// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.util

import java.sql.PreparedStatement

import scala.collection.immutable.ArraySeq
import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag

import slick.dbio.DBIO
import slick.jdbc._
import slick.jdbc.PostgresProfile.api._
import slick.sql._

import org.alephium.api.model.{Address => ApiAddress}
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

  @SuppressWarnings(Array("org.wartremover.warts.Overloading"))
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
      val query = action.sql
      new StreamingInvokerAction[ArraySeq[R], R, Effect] {
        def statements: Iterable[String] = List(query)
        protected[this] def createInvoker(statements: Iterable[String]): Invoker[R] =
          new StatementInvoker[R] {
            val getStatement = statements.head
            protected def setParam(st: PreparedStatement) =
              action.setParameter((), new PositionedParameters(st))
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
      val query = action.sql
      new StreamingInvokerAction[ArraySeq[R], R, Effect] {
        def statements: Iterable[String] = List(query)
        protected[this] def createInvoker(statements: Iterable[String]): Invoker[R] =
          new StatementInvoker[R] {
            val getStatement = statements.head
            protected def setParam(st: PreparedStatement) =
              action.setParameter((), new PositionedParameters(st))
            protected def extractValue(rs: PositionedResult): R = rconv(rs)
          }
        protected[this] def createBuilder = ArraySeq.newBuilder[R]
      }
    }

    def paginate(pagination: Pagination): SQLActionBuilder = {

      val parameters: SetParameter[Unit] =
        (_: Unit, params: PositionedParameters) => {
          action.setParameter((), params)
          params >> pagination.limit
          params >> pagination.offset
        }

      action.copy(
        sql = action.sql ++ "LIMIT ? OFFSET ?",
        setParameter = parameters
      )
    }
  }

  @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
  def addressColumn(
      address: ApiAddress,
      full: String = "address",
      half: String = "groupless_address"
  ): String = {
    address.lockupScript match {
      case _: ApiAddress.HalfDecodedLockupScript =>
        half
      case _ =>
        full
    }
  }

  def splitAddresses(
      addresses: ArraySeq[ApiAddress]
  ): (ArraySeq[ApiAddress], ArraySeq[ApiAddress]) = {
    addresses.partitionMap { address =>
      address.lockupScript match {
        case lockupScript: ApiAddress.HalfDecodedLockupScript =>
          Right(ApiAddress(lockupScript))
        case ApiAddress.CompleteLockupScript(lockupScript) =>
          Left(ApiAddress.from(lockupScript))
      }
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
