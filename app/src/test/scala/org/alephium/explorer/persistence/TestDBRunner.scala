// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence

import org.scalatest.concurrent.ScalaFutures
import slick.jdbc.PostgresProfile.api._

trait TestDBRunner extends DBRunner with ScalaFutures {
  def exec[T](action: DBIO[T]): T               = run(action).futureValue
  def execFailed[T](action: DBIO[T]): Throwable = run(action).failed.futureValue
}
