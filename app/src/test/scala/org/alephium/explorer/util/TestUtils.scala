// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only
package org.alephium.explorer.util

import slick.basic.{BasicProfile, DatabaseConfig}

object TestUtils {

  /** Mini [[scala.util.Using]] but unlike `Using` it does not return a [[scala.util.Try]].
    *
    * For test cases `Try` is not needed. We just want to return the test result and close
    * immediately throwing any failures with stacktrace.
    *
    * @param resource
    *   Closeable resource
    * @param code
    *   Test code to run
    * @tparam R
    *   Resource type
    * @tparam A
    *   Result type
    *
    * @return
    *   Result of type [[A]]
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
