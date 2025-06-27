// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.benchmark.db

import scala.concurrent.duration._

/** Default settings for executing benchmarks.
  */
object BenchmarkSettings {

  /** Default number of rows to generated for read benchmarks.
    *
    * @note
    *   When modified also ensure that timeout set via [[batchWriteTimeout]] is enough for creating
    *   this size data.
    */
  val readDataCount: Int = 1000000

  /** Default timeout for simple requests eg: read, drop & creating tables */
  val requestTimeout: FiniteDuration = 2.second

  /** Default timeout for persisting data used by read benchmarks */
  val batchWriteTimeout: FiniteDuration = 5.minutes

  /** Database name */
  val dbName: String = "benchmarks"

  /** Database host */
  val dbHost: String = "localhost"

  /** Database port */
  val dbPort: Int = 5432

}
