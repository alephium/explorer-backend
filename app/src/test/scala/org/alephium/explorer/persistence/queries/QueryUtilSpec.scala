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

class QueryUtilSpec extends AlephiumSpec {

  //A query's parameter row
  case class Row(param1: Int, param2: Int)

  object Query {
    //empty or initial Query
    val empty = Query(rows = Seq.empty, placeHolders = "")

    //helper function
    def apply(row: Row, placeHolders: String) =
      new Query(Seq(row), placeHolders)
  }

  /**
    * A single query.
    *
    * @param rows parameters for the query
    * @param placeHolders placeholder for the query
    */
  case class Query(rows: Iterable[Row], placeHolders: String)

  /**
    * Calls [[QueryUtil.splitFoldLeft]] and simple returns the result returned by it to test
    * the split's result.
    *
    * Each split is a single [[Query]] object which contains its [[Row]] and a placeholder String.
    */
  def getQueries(rows: Seq[Row], initialResult: Seq[Query], queryMaxParams: Int): Seq[Query] =
    QueryUtil.splitFoldLeft[Row, Seq[Query]](
      rows         = rows,
      initialQuery = initialResult,
      //Number of parameters in Row. For this test it's param1 & param2 = 2
      queryRowParams = 2,
      queryMaxParams = queryMaxParams
    ) { (rows, placeHolders, result) =>
      //merge all queries in a single collection
      result :+ Query(rows, placeHolders)
    }

  it should "empty split when parameters are empty" in {

    forAll(Gen.posNum[Int]) { queryMaxParams =>
      val queries =
        getQueries(
          rows           = Seq.empty,
          initialResult  = Seq.empty,
          queryMaxParams = queryMaxParams
        )

      //parameters are empty so queries should be empty
      queries is List.empty
    }
  }

  it should "split queries evenly when queryColumns & queryMaxParams are even" in {
    val queries =
      getQueries(
        rows           = Seq(Row(1, 1), Row(2, 2), Row(3, 3), Row(4, 4), Row(5, 5), Row(6, 6)),
        initialResult  = Seq.empty,
        queryMaxParams = 4 //max number of params per query
      )

    //expect 4 parameters per query
    val expectedQueries =
      Seq(
        Query(List(Row(1, 1), Row(2, 2)), "(?, ?),\n(?, ?)"),
        Query(List(Row(3, 3), Row(4, 4)), "(?, ?),\n(?, ?)"),
        Query(List(Row(5, 5), Row(6, 6)), "(?, ?),\n(?, ?)")
      )

    queries is expectedQueries
  }

  it should "split queries when queryColumns & queryMaxParams are odd" in {
    val queries =
      getQueries(
        //7 parameters
        rows           = Seq(Row(1, 1), Row(2, 2), Row(3, 3), Row(4, 4), Row(5, 5), Row(6, 6), Row(7, 7)),
        initialResult  = Seq.empty,
        queryMaxParams = 4 //max number of params per query
      )

    //expect 4 parameters per query but the last/odd gets it own query
    val expectedQueries =
      List(
        Query(Seq(Row(1, 1), Row(2, 2)), "(?, ?),\n(?, ?)"),
        Query(Seq(Row(3, 3), Row(4, 4)), "(?, ?),\n(?, ?)"),
        Query(Seq(Row(5, 5), Row(6, 6)), "(?, ?),\n(?, ?)"),
        Query(Seq(Row(7, 7)), "(?, ?)")
      )

    queries is expectedQueries
  }

  it should "split when queryColumns == queryMaxParams" in {
    val queries =
      getQueries(
        rows           = Seq(Row(1, 1), Row(2, 2), Row(3, 3), Row(4, 4), Row(5, 5), Row(6, 6), Row(7, 7)),
        initialResult  = Seq.empty,
        queryMaxParams = 2 //2 parameters per row
      )

    //expect each query to get a single row
    val expectedQueries =
      List(
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

  it should "split should generate a single query when all parameter can fit in a single query" in {
    val queries =
      getQueries(
        rows           = Seq(Row(1, 1), Row(2, 2), Row(3, 3), Row(4, 4), Row(5, 5), Row(6, 6), Row(7, 7)),
        initialResult  = Seq.empty,
        queryMaxParams = 7 * 2 //allow all parameters to fit in a single query
      )

    //Expected a single query
    val expectedQuery =
      Query(
        rows         = List(Row(1, 1), Row(2, 2), Row(3, 3), Row(4, 4), Row(5, 5), Row(6, 6), Row(7, 7)),
        placeHolders = "(?, ?),\n(?, ?),\n(?, ?),\n(?, ?),\n(?, ?),\n(?, ?),\n(?, ?)"
      )

    queries should contain only expectedQuery
  }
}
