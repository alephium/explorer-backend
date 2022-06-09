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
package org.alephium.explorer.util

import slick.basic.{BasicProfile, DatabaseConfig}

object TestUtils {

  /**
    * Mini [[scala.util.Using]] but unlike `Using` it does not return a [[scala.util.Try]].
    *
    * For test cases `Try` is not needed. We just want to return the test result and close immediately
    * throwing any failures with stacktrace.
    *
    * @param resource Closeable resource
    * @param code     Test code to run
    * @tparam R Resource type
    * @tparam A Result type
    *
    * @return Result of type [[A]]
    */
  def using[R <: AutoCloseable, A](resource: R)(code: R => A): A =
    try code(resource)
    finally resource.close()

  /** Using for DatabaseConfig */
  def using[P <: BasicProfile, A](db: DatabaseConfig[P])(code: DatabaseConfig[P] => A): A =
    using(db.db) { _ =>
      code(db)
    }
}
