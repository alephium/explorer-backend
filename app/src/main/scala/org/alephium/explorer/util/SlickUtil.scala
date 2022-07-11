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

import slick.dbio.DBIO

import org.alephium.explorer.persistence.{DBActionR, DBActionSR}

/** Convenience functions for Slick */
object SlickUtil {

  implicit class ResultEnrichment[A](val action: DBActionSR[A]) extends AnyVal {

    /**
      * Fetch single row else fail.
      */
    @SuppressWarnings(Array("org.wartremover.warts.IterableOps"))
    def exactlyOne(implicit ec: ExecutionContext): DBActionR[A] =
      action.flatMap { rows =>
        rows.size match {
          case 1 => DBIO.successful(rows.head)
          case n => DBIO.failed(new RuntimeException(s"Expected 1 result, actual $n"))
        }
      }
  }

  def paramPlaceholderTuple2(rows: Int, columns: Int): String =
    paramPlaceholder(rows, columns, "(?, ?)")

  @SuppressWarnings(Array("org.wartremover.warts.Overloading"))
  def paramPlaceholder(rows: Int, columns: Int): String =
    paramPlaceholder(rows, columns, "?")

  /**
    * Builds placeholders for generating parameterised SQL queries.
    *
    * Example: If rows = 2, columns = 3 & placeHolder = "?" this
    *          returns comma separated rows (?, ?, ?),(?, ?, ?).
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
