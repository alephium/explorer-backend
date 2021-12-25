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

package org.alephium.explorer.benchmark.postgres

import com.typesafe.scalalogging.StrictLogging
import org.openjdk.jmh.annotations.{Level, Setup, TearDown}
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.benchmark.CurrentThreadExecutionContext.self

/**
  * Base implementation for JMH states, for benchmarking write queries to PostgresSQL.
  *
  * It mimics [[Iterator]]'s API as much as possible using JMH annotations.
  *
  * JMH annotations used create the following lifecycle of function invocations
  *  - [[setNext]] - Set the next data for the benchmark's current iteration's invocation
  *  - [[next]] - Returns the data set by [[setNext]]
  *
  * @param generateData Function that generates random data
  * @param table        The table being benchmarked
  * @tparam D The type of data. Currently this is either of the following
  *           - `Array[Byte]` - For bytea
  *           - `String` - For varchar
  * @tparam T The type of table
  */
abstract class WriteBenchStateBase[D, T <: Table[_]](generateData: () => D,
                                                     val table: TableQuery[T])
    extends StrictLogging
    with PostgresBenchState[T] {

  /**
    * Setter - placeholder to store next data required by the benchmark.
    * Populated when JMH invokes [[setNext]]
    */
  private var _next: D = _

  //getter. Similar to an Iterator's next function.
  def next: D = _next

  //database connection
  val connection: PostgresConnection[T] =
    PostgresConnection(
      dbName = "bytea-vs-varchar-write-benchmark",
      table  = table
    )

  //Executed on every invocation of the benchmark function which set the next data to benchmark
  @Setup(Level.Invocation)
  def setNext(): Unit =
    this._next = generateData()

  @TearDown(Level.Iteration)
  def terminateBlocking(): Unit = {
    logger.info(s"Executing tearDown for table: ${table.baseTableRow.tableName}")
    connection.terminateBlocking()
  }
}
