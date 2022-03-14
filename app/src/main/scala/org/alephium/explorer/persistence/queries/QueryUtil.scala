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

import scala.annotation.tailrec

import slick.dbio.DBIOAction

import org.alephium.explorer.persistence.DBActionW
import org.alephium.explorer.persistence.schema.CustomSetParameter.paramPlaceholder

object QueryUtil {

  /** Maximum number of parameters allowed by query.
    *
    * Most of our tables have around 10 columns, so having 2000 makes around 200 entries per query
    *
    * TODO: This should be passed as a configuration instead of hardcoding it
    *       and should be passed as parameter to the function using this value.
    * */
  val parameterLimit: Short = 2000

  private val emptyUpdates = DBIOAction.successful(0)

  /** Splits update queries into batches limited by the total number parameters allowed per query */
  @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
  def splitUpdates[R](rows: Iterable[R],
                      queryRowParams: Int,
                      queryMaxParams: Short = parameterLimit)(
      queryBuilder: (Iterable[R], String) => DBActionW[Int]): DBActionW[Int] =
    splitFoldLeft[R, DBActionW[Int]](initialQuery   = emptyUpdates,
                                     queryRowParams = queryRowParams,
                                     queryMaxParams = queryMaxParams,
                                     rows           = rows) { (rows, placeholder, action) =>
      action andThen queryBuilder(rows, placeholder)
    }

  /**
    * Splits queries into batches limited by the total number parameters allowed per query.
    *
    * @param rows             All query parameters
    * @param initialQuery     Returned when params are empty
    * @param queryRowParams   Max number of parameters in a single row of a query. Or the number of '?' in a single row.
    * @param queryMaxParams   Maximum number of parameters for each query. Eg: [[parameterLimit]]
    * @param foldLeft         Given a set of following inputs (Tuple3) returns the next query.
    *                         Similar to foldLeft in a collection type.
    *                           - _1 = Parameter split for current query
    *                           - _2 = Placeholder for current query
    *                           - _3 = Previous query. Used to join with next query.
    * @tparam R type of rows
    * @tparam Q type of query
    * @return A single query of type [[Q]]
    */
  def splitFoldLeft[R, Q](rows: Iterable[R],
                          initialQuery: Q,
                          queryRowParams: Int,
                          queryMaxParams: Short)(foldLeft: (Iterable[R], String, Q) => Q): Q = {

    //maximum number of rows per query
    val maxRows = queryMaxParams / queryRowParams

    @tailrec
    def build(rowsRemaining: Iterable[R], previousQuery: Q): Q =
      if (rowsRemaining.isEmpty) {
        previousQuery
      } else {
        //number of rows for this query
        val queryRows = rowsRemaining.size min maxRows
        //generate placeholder string
        val placeholder = paramPlaceholder(rows = queryRows, columns = queryRowParams)

        //thisBatch = rows for this query
        //remaining = rows not processed in this query
        val (thisBatch, remaining) = rowsRemaining.splitAt(queryRows)
        val nextResult             = foldLeft(thisBatch, placeholder, previousQuery)

        //process remaining
        build(rowsRemaining = remaining, previousQuery = nextResult)
      }

    build(rowsRemaining = rows, previousQuery = initialQuery)
  }
}
