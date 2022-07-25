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
import org.alephium.explorer.util.SlickUtil.paramPlaceholder

object QuerySplitter {

  /** Maximum number of parameters per query allowed by Postgres.
    *
    * @note This value cannot be > [[scala.Short.MaxValue]].
    *       See issue <a href="https://github.com/alephium/explorer-backend/issues/160">#160</a>.
    *
    * TODO: This should be passed as a configuration instead of hardcoding it
    *       and should be passed as parameter to the function using this value.
    * */
  val postgresDefaultParameterLimit: Short = Short.MaxValue

  /** Default value for maximum number rows per query */
  val defaultMaxRowsPerQuery: Short = 200

  private val emptyUpdates = DBIOAction.successful(0)

  /** Splits update queries into batches limited by the total number parameters allowed per query */
  @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
  def splitUpdates[R](rows: Iterable[R],
                      columnsPerRow: Short,
                      maxRowsPerQuery: Short = defaultMaxRowsPerQuery)(
      queryBuilder: (Iterable[R], String) => DBActionW[Int]): DBActionW[Int] =
    splitFoldLeft[R, DBActionW[Int]](initialQuery    = emptyUpdates,
                                     columnsPerRow   = columnsPerRow,
                                     maxRowsPerQuery = maxRowsPerQuery,
                                     rows            = rows) { (rows, placeholder, action) =>
      action andThen queryBuilder(rows, placeholder)
    }

  /**
    * Splits queries into batches limited by the total number of rows allowed per query.
    *
    * @param rows                All query parameters
    * @param initialQuery        Returned when params are empty
    * @param columnsPerRow       Total number of columns/parameters in a single row of a query.
    *                            Or the number of '?' in a single row.
    * @param maxRowsPerQuery     Maximum number of rows in each query. Eg: [[defaultMaxRowsPerQuery]]
    * @param foldLeft            Given a set of following inputs (Tuple3) returns the next query.
    *                            Similar to foldLeft in a collection type.
    *                              - _1 = Parameter split for current query
    *                              - _2 = Placeholder for current query
    *                              - _3 = Previous query. Used to join with next query.
    * @tparam R type of rows
    * @tparam Q type of query
    * @return A single batched query
    */
  def splitFoldLeft[R, Q](rows: Iterable[R],
                          initialQuery: Q,
                          columnsPerRow: Short,
                          maxRowsPerQuery: Short)(foldLeft: (Iterable[R], String, Q) => Q): Q = {
    //calculate maximum number of parameters to allow per query
    val maxParamsPerQuery =
      maxRowsPerQuery * columnsPerRow

    //maxParamsPerQuery should not exceed max limit allowed by Postgres i.e. postgresDefaultParameterLimit
    val maxParamsPerQueryAllowed =
      (maxParamsPerQuery min postgresDefaultParameterLimit.toInt).toShort

    splitFoldLeftLimitByMaxParams[R, Q](
      rows              = rows,
      initialQuery      = initialQuery,
      columnsPerRow     = columnsPerRow,
      maxParamsPerQuery = maxParamsPerQueryAllowed
    )(foldLeft)
  }

  /**
    * Splits queries into batches limited by maximum/total number of parameters allowed per query.
    *
    * @param rows                All query parameters
    * @param initialQuery        Returned when params are empty
    * @param columnsPerRow       Total number of columns/parameters in a single row of a query.
    *                            Or the number of '?' in a single row.
    * @param maxParamsPerQuery   Maximum number of parameters for each query. Eg: [[postgresDefaultParameterLimit]]
    * @param foldLeft            Given a set of following inputs (Tuple3) returns the next query.
    *                            Similar to foldLeft in a collection type.
    *                              - _1 = Parameter split for current query
    *                              - _2 = Placeholder for current query
    *                              - _3 = Previous query. Used to join with next query.
    * @tparam R type of rows
    * @tparam Q type of query
    * @return A single batched query
    */
  def splitFoldLeftLimitByMaxParams[R, Q](
      rows: Iterable[R],
      initialQuery: Q,
      columnsPerRow: Short,
      maxParamsPerQuery: Short)(foldLeft: (Iterable[R], String, Q) => Q): Q = {

    //maximum number of rows per query
    val maxRows = maxParamsPerQuery / columnsPerRow

    val columnsPerRowInt = columnsPerRow.toInt

    @tailrec
    def build(rowsRemaining: Iterable[R], previousQuery: Q): Q =
      if (rowsRemaining.isEmpty) {
        previousQuery
      } else {
        //number of rows for this query
        val queryRows = rowsRemaining.size min maxRows
        //generate placeholder string
        val placeholder = paramPlaceholder(rows = queryRows, columns = columnsPerRowInt)

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
