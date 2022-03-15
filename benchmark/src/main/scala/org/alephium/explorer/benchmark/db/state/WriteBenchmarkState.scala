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

import com.typesafe.scalalogging.StrictLogging
import org.openjdk.jmh.annotations.{Level, Setup, TearDown}
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import org.alephium.explorer.benchmark.db.DBExecutor

/**
  * Base implementation for JMH states, for benchmarking write queries to the target database.
  *
  * JMH annotations used create the following lifecycle of function invocations
  *  - 1. [[beforeAll]] - Implemented by caller. For eg: Drop and create a new table.
  *  - 2. [[beforeEach]] - Set the next data for the benchmark's current iteration's invocation
  *  - 3. [[next]] - Returns the data set by [[beforeEach]]. Used by the actual benchmark code in [[DBBenchmark]].
  *  - 4. [[afterAll]] - Terminates db connection created by [[beforeAll]]
  *
  * @param db Target database
  * @tparam D Data type of the targeted table
  */
abstract class WriteBenchmarkState[D](db: DBExecutor) extends AutoCloseable with StrictLogging {

  val config: DatabaseConfig[PostgresProfile] =
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

  /** Generates random data of type [[D]] */
  def generateData(): D

  /** Invoked once before a benchmark is started. Used to create a desired state for eg: create/clearing a table */
  @Setup(Level.Iteration)
  def beforeAll(): Unit

  /** Invoked on every invocation of the benchmark function which set the next data to benchmark */
  @Setup(Level.Invocation)
  def beforeEach(): Unit =
    this._next = generateData()

  /** Invoked at the end of the benchmark iteration */
  @TearDown(Level.Iteration)
  def afterAll(): Unit = {
    logger.info("Closing DB connection")
    db.close()
  }

  override def close(): Unit =
    afterAll()
}
