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

package org.alephium.explorer.benchmark.postgres.util

import java.util.concurrent.RejectedExecutionException

import org.scalatest.concurrent.ScalaFutures._
import org.scalatest.exceptions.TestFailedException
import org.scalatest.matchers.should.Matchers._
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.benchmark.CurrentThreadExecutionContext.self
import org.alephium.explorer.benchmark.postgres.PostgresBenchState

/**
  * Implements common used test functions
  */
object TestKit {

  /**
    * TODO: Should this be renamed to TestUtil or some other naming convention?
    *
    * Creates and safely releases resources (eg: postgres connection)
    * created by the input `stateCreator`.
    *
    * Example use:
    * {{{
    *   it should "do something" in withState(MyBenchState()) {
    *      state =>
    *        //your test code here
    *  }
    * }}}
    *
    * @param stateCreator Creates a new instance of the state
    * @param testCode     Test to execute
    * @tparam S The state being tested
    */
  def withState[S <: PostgresBenchState[_]](stateCreator: => S)(testCode: S => Unit): Unit = {
    val state = stateCreator
    try {
      testCode(state)
    } finally {
      //terminate
      state.terminateBlocking()
      //querying the connection after termination should get rejected
      //scalastyle:off scalatest-matcher
      intercept[TestFailedException] {
        state.connection.run(state.table.result).futureValue
      }.getCause.getCause shouldBe a[RejectedExecutionException]
      ()
      //scalastyle:on scalatest-matcher>
    }
  }
}
