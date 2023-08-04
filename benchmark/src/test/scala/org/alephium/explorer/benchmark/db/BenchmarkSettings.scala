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
