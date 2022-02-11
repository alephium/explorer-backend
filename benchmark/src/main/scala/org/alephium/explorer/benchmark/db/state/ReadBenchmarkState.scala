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

package org.alephium.explorer.benchmark.db.state

import scala.annotation.tailrec
import scala.reflect.ClassTag

import com.typesafe.scalalogging.StrictLogging
import org.openjdk.jmh.annotations._
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import org.alephium.explorer.benchmark.db.DBExecutor

/**
  * Base implementation for JMH states, for benchmarking reads queries to the target database.
  *
  * JMH annotations used here create the following lifecycle of function invocations (naming similar to ScalaTest)
  *  - 1. [[beforeAll]] - Invoked by JMH before the benchmark is executed.
  *  - 2. [[beforeEach]] - Set the [[next]] data for the benchmark's current iteration.
  *  - 3. [[next]] - Returns the data set by [[beforeEach]]. Used by the actual benchmark code in [[DBBenchmark]].
  *  - 4. [[afterAll]] - Terminates db connection created by [[beforeAll]].
  *
  * @param testDataCount Total number of rows to persist in [[beforeAll]]
  * @param db            Target database
  * @tparam D Data type of the targeted table
  */
abstract class ReadBenchmarkState[D: ClassTag](val testDataCount: Int, db: DBExecutor)
    extends AutoCloseable
    with StrictLogging {

  //testDataCount cannot be 0 or negative value
  require(testDataCount >= 1, s"Invalid testDataCount '$testDataCount'. It should be >= 1.")

  val config: DatabaseConfig[JdbcProfile] =
    db.config

  /**
    * Placeholder to store next data required by the benchmark.
    * Populated when JMH invokes [[beforeEach]].
    */
  private var _next: D = _

  /**
    * Getter. Similar to an Iterator.next function this
    * provides the next available data to read.
    *
    * @note Prerequisite: [[beforeEach]] should be invoked before next is called.
    *       JMH will automatically call [[beforeEach]]. Your benchmark function only
    *       needs to invoke [[next]].
    */
  def next: D = _next

  /** Position of current data being benchmarked */
  private var nextIndex = 0

  /** stores all test data. Populated by generateTestData */
  val cache: Array[D] =
    new Array[D](testDataCount)

  /** Generates random data of type [[D]] */
  def generateData(currentCacheSize: Int): D

  /** Invoked once during [[beforeAll]]. It should persist the input data to the target database. */
  def persist(data: Array[D]): Unit

  /** Invoked once before a benchmark is started. Generates, persists & caches data required by the benchmark */
  @Setup(Level.Iteration)
  def beforeAll(): Unit = {
    logger.info(s"Generating test data of size $testDataCount")

    for (index <- 0 until testDataCount)
      cache(index) = generateData(index)

    persist(cache)

    logger.info("Generation complete.")
  }

  /** Invoked on every invocation of the benchmark function which set the next data to benchmark */
  @Setup(Level.Invocation)
  @tailrec
  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  final def beforeEach(): Unit =
    try {
      //set the next data to query required by benchmark
      _next = cache(nextIndex)
      nextIndex += 1
      //Logs how much data from the cache is read
      if (nextIndex % (cache.length / 100D) == 0) {
        //logger.info(s"Read progress: $nextIndex/${cache.length}")
      }
    } catch {
      case exception: ArrayIndexOutOfBoundsException =>
        if (nextIndex == 0) {
          //if its already zero then testDataCount must be invalid (<= 0)
          //throw exception indicating invalid testDataCount input
          throw exception
        } else {
          //Else it's finished reading all rows. Reset index so benchmarking reads continue.
          nextIndex = 0
          beforeEach()
        }
    }

  /** Invoked at the end of the benchmark iteration */
  @TearDown(Level.Iteration)
  def afterAll(): Unit = {
    logger.info("Closing DB connection")
    db.close()
  }

  override def close(): Unit =
    afterAll()
}
