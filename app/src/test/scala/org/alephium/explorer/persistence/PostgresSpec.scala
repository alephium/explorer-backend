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

package org.alephium.explorer.persistence

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

import org.scalatest.concurrent.ScalaFutures
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.AlephiumSpec
import org.alephium.util.TimeStamp

/**
  * Postgres specific test-cases.
  */
class PostgresSpec
    extends AlephiumSpec
    with DatabaseFixtureForEach
    with DBRunner
    with ScalaFutures {

  implicit val executionContext: ExecutionContext = ExecutionContext.global

  it should "return UTC timestamp equalling TimeStamp.now()" in {
    forAll { _: Int =>
      //JVM timestamp
      val jvmNow = TimeStamp.now()

      //SQL timestamp
      val sqlNow = run(sql"select floor(extract(epoch from now()) * 1000)".as[Long]).futureValue
      sqlNow should have size 1

      val difference = sqlNow.head - jvmNow.millis
      //The difference should be positive and <= 500.milliseconds.
      //Actual difference seen is <= 10.milliseconds but 500 is used to cover latency cost (just in-case)
      difference should (be >= 0L and be <= 500.millisecond.toMillis)
    }
  }
}
