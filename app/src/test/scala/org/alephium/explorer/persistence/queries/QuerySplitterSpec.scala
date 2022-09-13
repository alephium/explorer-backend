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

import org.scalacheck.Gen

import org.alephium.explorer.AlephiumSpec
import org.alephium.util.AVector

class QuerySplitterSpec extends AlephiumSpec {

  //A query's parameter row
  case class Row(param1: Int, param2: Int)

  object Query {
    //empty or initial Query
    val empty: Query =
      Query(rows = AVector.empty[Row], placeHolders = "")

    //helper function
    def apply(row: Row, placeHolders: String) =
      new Query(AVector(row), placeHolders)
  }

  /**
    * A single query.
    *
    * @param rows parameters for the query
    * @param placeHolders placeholder for the query
    */
  case class Query(rows: AVector[Row], placeHolders: String)

  /**
    * Calls [[QuerySplitter.splitFoldLeft]] and simple returns the result returned by it to test
    * the split's result.
    *
    * Each split is a single [[Query]] object which contains its [[Row]] and a placeholder String.
    */
  def getQueriesLimitByMaxParams(rows: AVector[Row],
                                 initialResult: AVector[Query],
                                 queryMaxParams: Short): AVector[Query] =
    QuerySplitter.splitFoldLeftLimitByMaxParams[Row, AVector[Query]](
      rows         = rows,
      initialQuery = initialResult,
      //Number of parameters in Row. For this test it's param1 & param2 = 2
      columnsPerRow     = 2,
      maxParamsPerQuery = queryMaxParams
    ) { (rows, placeHolders, result) =>
      //merge all queries in a single collection
      result :+ Query(rows, placeHolders)
    }

  "empty split when parameters are empty" in {

    forAll(Gen.posNum[Short]) { queryMaxParams =>
      val queries =
        getQueriesLimitByMaxParams(
          rows           = AVector.empty,
          initialResult  = AVector.empty,
          queryMaxParams = queryMaxParams
        )

      //parameters are empty so queries should be empty
      queries is AVector.empty[Query]
    }
  }

  "split queries evenly when queryColumns & queryMaxParams are even" in {
    val queries =
      getQueriesLimitByMaxParams(
        rows           = AVector(Row(1, 1), Row(2, 2), Row(3, 3), Row(4, 4), Row(5, 5), Row(6, 6)),
        initialResult  = AVector.empty,
        queryMaxParams = 4 //max number of params per query
      )

    //expect 4 parameters per query
    val expectedQueries =
      AVector(
        Query(AVector(Row(1, 1), Row(2, 2)), "(?, ?),(?, ?)"),
        Query(AVector(Row(3, 3), Row(4, 4)), "(?, ?),(?, ?)"),
        Query(AVector(Row(5, 5), Row(6, 6)), "(?, ?),(?, ?)")
      )

    queries is expectedQueries
  }

  "split queries when queryColumns & queryMaxParams are odd" in {
    val queries =
      getQueriesLimitByMaxParams(
        //7 parameters
        rows           = AVector(Row(1, 1), Row(2, 2), Row(3, 3), Row(4, 4), Row(5, 5), Row(6, 6), Row(7, 7)),
        initialResult  = AVector.empty,
        queryMaxParams = 4 //max number of params per query
      )

    //expect 4 parameters per query but the last/odd gets it own query
    val expectedQueries =
      AVector(
        Query(AVector(Row(1, 1), Row(2, 2)), "(?, ?),(?, ?)"),
        Query(AVector(Row(3, 3), Row(4, 4)), "(?, ?),(?, ?)"),
        Query(AVector(Row(5, 5), Row(6, 6)), "(?, ?),(?, ?)"),
        Query(AVector(Row(7, 7)), "(?, ?)")
      )

    queries is expectedQueries
  }

  "split when queryColumns == queryMaxParams" in {
    val queries =
      getQueriesLimitByMaxParams(
        rows           = AVector(Row(1, 1), Row(2, 2), Row(3, 3), Row(4, 4), Row(5, 5), Row(6, 6), Row(7, 7)),
        initialResult  = AVector.empty,
        queryMaxParams = 2 //2 parameters per row
      )

    //expect each query to get a single row
    val expectedQueries =
      AVector(
        Query(Row(1, 1), "(?, ?)"),
        Query(Row(2, 2), "(?, ?)"),
        Query(Row(3, 3), "(?, ?)"),
        Query(Row(4, 4), "(?, ?)"),
        Query(Row(5, 5), "(?, ?)"),
        Query(Row(6, 6), "(?, ?)"),
        Query(Row(7, 7), "(?, ?)")
      )

    queries is expectedQueries
  }

  "split should generate a single query when all parameter can fit in a single query" in {
    val queries =
      getQueriesLimitByMaxParams(
        rows           = AVector(Row(1, 1), Row(2, 2), Row(3, 3), Row(4, 4), Row(5, 5), Row(6, 6), Row(7, 7)),
        initialResult  = AVector.empty,
        queryMaxParams = 7 * 2 //allow all parameters to fit in a single query
      )

    //Expected a single query
    val expectedQuery =
      Query(
        rows         = AVector(Row(1, 1), Row(2, 2), Row(3, 3), Row(4, 4), Row(5, 5), Row(6, 6), Row(7, 7)),
        placeHolders = "(?, ?),(?, ?),(?, ?),(?, ?),(?, ?),(?, ?),(?, ?)"
      )

    queries containsOnly expectedQuery
  }

  "Test following PR review comment in #162: See link below" in {

    /**
      * Test following <a href="https://github.com/alephium/explorer-backend/pull/162#discussion_r826874299">comment</a>.
      *
      * Let's say the limit of parameters (i.e. ?) is N, and each query has M parameters,
      * then each insertion can insert upto N/M rows. If N = 200 and M=10, then each batch
      * can insert at most 20 rows. This would be very slow. However, if test with N = 2000,
      * then each batch would insert 200 rows. Let me know if I am clear enough now.
      */
    //a table row with 10 columns == 10 query params ('?') per row
    case class Row(col1: Int,
                   col2: Int,
                   col3: Int,
                   col4: Int,
                   col5: Int,
                   col6: Int,
                   col7: Int,
                   col8: Int,
                   col9: Int,
                   col10: Int)

    //The foldLeft function below simply returns the rows and placeholder
    //generated per batch/chunk. This data type holds that information.
    case class Query(rows: AVector[Row], placeholder: String)

    //let's generate some data - 10,000 rows
    val rows = (1 to 10000).map(i => Row(i, i, i, i, i, i, i, i, i, i))

    //From comment: M=10
    //Number of columns per row or number of params '?' per row
    val columnsPerRows = 10.toShort

    //Check that maxParametersPerQuery is 2000 and valid to use in this test case
    QuerySplitter.defaultMaxRowsPerQuery is 200.toShort

    val querySplits: AVector[Query] =
      QuerySplitter.splitFoldLeft[Row, AVector[Query]](
        rows            = rows,
        initialQuery    = AVector.empty,
        columnsPerRow   = columnsPerRows,
        maxRowsPerQuery = QuerySplitter.defaultMaxRowsPerQuery
      ) { (rowsForThisQuery, placeholderForThisQuery, allQueries) =>
        //collect all queries
        allQueries :+ Query(rowsForThisQuery, placeholderForThisQuery)
      }

    //From comment: then each batch would insert 200 rows
    querySplits.length is (10000 / 200)

    querySplits.zipWithIndex foreach {
      case (split, index) =>
        //From comment: then each batch would insert 200 rows
        //Assert each query gets 200 rows
        split.rows should have length 200
        //Assert each split has correct rows params
        val drop = index * 200
        split.rows is rows.slice(drop, drop + 200)
        //Assert each column has 10 '?'
        split.placeholder.count(_ == '?') is (200 * 10)
    }

    //all rows get assigned
    querySplits.flatMap(_.rows) is rows
  }

  "not exceed Short.MaxValue parameter per query limit allowed by Postgres" in {

    /** In reality this test-case is not expected to occur but for the sake of totality it is tested */
    //A row. This test doesn't check for actual column values so a Row object is enough.
    case object Row

    //The foldLeft function below simply returns the rows and placeholder
    //generated per batch/chunk. This data type holds that information.
    case class Query(rows: AVector[Row.type], placeholder: String)

    //let's generate some data - 10,000 rows
    val rows = (1 to 10000).map(_ => Row)

    //Number of columns per row or number of params '?' per row
    //This will allow maxParamsPerQuery to be 1,638,200 which is over Short.MaxValue limit
    val columnsPerRows: Short = (QuerySplitter.postgresDefaultParameterLimit / 4).toShort

    //Check that maxParametersPerQuery is 200 and valid to use in this test case
    QuerySplitter.defaultMaxRowsPerQuery is 200.toShort

    val querySplits: AVector[Query] =
      QuerySplitter.splitFoldLeft[Row.type, AVector[Query]](
        rows            = rows,
        initialQuery    = AVector.empty,
        columnsPerRow   = columnsPerRows,
        maxRowsPerQuery = QuerySplitter.defaultMaxRowsPerQuery
      ) { (rowsForThisQuery, placeholderForThisQuery, allQueries) =>
        //collect all queries
        allQueries :+ Query(rowsForThisQuery, placeholderForThisQuery)
      }

    querySplits.length is (rows.length / 4)

    querySplits foreach { split =>
      //Assert each query gets 4 rows because 4 rows satisfy max params limit set by Postgres configured
      //via QuerySplitter.postgresDefaultParameterLimit
      split.rows.length is 4
      //Assert each column has ((columnsPerRows * 4) * '?') = 32764
      split.placeholder.count(_ == '?') is (columnsPerRows * 4)
    }

    //all rows get assigned
    querySplits.foldLeft(0)(_ + _.rows.length) is rows.length
  }

}
