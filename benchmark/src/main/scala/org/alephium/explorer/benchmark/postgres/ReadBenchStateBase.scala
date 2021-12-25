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

import scala.annotation.tailrec
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.reflect.ClassTag

import com.typesafe.scalalogging.StrictLogging
import org.openjdk.jmh.annotations._
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.benchmark.CurrentThreadExecutionContext.self

/**
  * Base implementation for  JMH states, for benchmarking reads queries to PostgresSQL.
  *
  * It mimics [[Iterator]]'s API as much as possible using JMH annotations.
  *
  * JMH annotations used here create the following lifecycle of function invocations
  *  - 1. [[generateTestData]] - Generates data. Called once on instantiation.
  *  - 2. [[setNext]] - Set the next data for the benchmark's current iteration's invocation
  *  - 3. [[next]] - Returns the data set by [[setNext]]
  *
  * @param generateData  A function that generates random data for the table
  * @param table         The table being benchmarked
  * @param testDataCount Total number of rows to insert during setup
  * @tparam D The type of data. Currently this is either of the following
  *           - `Array[Byte]` - For `bytea`
  *           - `String` - For `varchar`
  * @tparam T The type of [[table]]
  */
abstract class ReadBenchStateBase[D: ClassTag, T <: Table[D]](generateData: () => D,
                                                              val table: TableQuery[T],
                                                              val testDataCount: Int)
    extends StrictLogging
    with PostgresBenchState[T] {

  //testDataCount cannot be 0 or negative value
  require(testDataCount >= 1, s"Invalid testDataCount '$testDataCount'. It should be >= 1.")

  /**
    * Setter - placeholder to store next data required by the benchmark.
    * Populated when JMH invokes [[setNext]].
    */
  private var _next: D = _

  //getter. Similar to an Iterator's next function.
  def next: D = _next

  //position of current data being benchmarked
  private var nextIndex = 0

  //stores all test data. Populated by generateTestData
  val generatedTestData: Array[D] =
    new Array[D](testDataCount)

  //database connection
  val connection: PostgresConnection[T] =
    PostgresConnection(
      dbName = "bytea-vs-varchar-read-benchmark",
      table  = table
    )

  //invoked once before each JHM iteration
  @Setup(Level.Iteration)
  def generateTestData(): Unit = {
    logger.info(s"Generating test data of size $testDataCount")

    for (index <- 0 until testDataCount)
      generatedTestData(index) = generateData()

    val _ = Await.result(connection.run(connection.table ++= generatedTestData), 1.minute)

    logger.info(s"Generation complete.")
  }

  //Executed on every invocation of the benchmark function which set the next data to benchmark
  @Setup(Level.Invocation)
  @tailrec
  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  final def setNext(): Unit =
    try {
      //set the next data to query required by benchmark
      _next = generatedTestData(nextIndex)
      nextIndex += 1
    } catch {
      case exception: ArrayIndexOutOfBoundsException =>
        if (nextIndex == 0) {
          //if its already zero then testDataCount must be invalid (<= 0)
          //throw exception indicating invalid testDataCount input
          throw exception
        } else {
          //reset the read index from 0 so reads benchmarks continue
          nextIndex = 0
          setNext()
        }
    }

  //Invoked at the end of benchmark. Clears the tables.
  @TearDown(Level.Iteration)
  def terminateBlocking(): Unit =
    connection.terminateBlocking()
}
