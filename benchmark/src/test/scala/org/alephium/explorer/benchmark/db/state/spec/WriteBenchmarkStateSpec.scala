// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.benchmark.db.state.spec

import scala.concurrent.duration._

import org.alephium.explorer.AlephiumSpec
import org.alephium.explorer.benchmark.db.DBExecutor
import org.alephium.explorer.benchmark.db.state._
import org.alephium.explorer.util.TestUtils._

/** Tests the behaviour of functions implemented in [[WriteBenchmarkState]] using
  *   - [[ByteaWriteState]]
  *   - [[VarcharWriteState]]
  */
class WriteBenchmarkStateSpec extends AlephiumSpec {

  // total number of rows to generate
  val testDataCount = 10

  "beforeAll - generate a new empty table" in {
    def doTest[S <: WriteBenchmarkState[_]](state: => S, getRowCount: S => Int): Unit =
      using(state) { state =>
        state.beforeAll()
        // table is empty
        getRowCount(state) is 0
        ()
      }

    doTest[ByteaWriteState](
      new ByteaWriteState(DBExecutor.forTest()),
      state => {
        import state.config.profile.api._
        state.db.runNow(state.tableByteaQuery.length.result, 1.second)
      }
    )

    doTest[VarcharWriteState](
      new VarcharWriteState(DBExecutor.forTest()),
      state => {
        import state.config.profile.api._
        state.db.runNow(state.tableVarcharQuery.length.result, 1.second)
      }
    )
  }

  "beforeEach - generate data incrementally" in {
    using(new VarcharWriteState(DBExecutor.forTest())) { state =>
      // invoking next before setNext should return null
      // Option is not used here to avoid the cost of unnecessary
      // memory allocation for accurate benchmarking results
      // scalastyle:off
      state.next shouldBe null
      // scalastyle:off

      state.beforeEach() // generates and set the data
      val next1 = state.next // fetch the set data
      next1 isnot "" // is not empty

      state.beforeEach()
      val next2 = state.next
      next2 isnot ""    // is not empty
      next2 isnot next1 // it gets a new value

      state.beforeEach()
      val next3 = state.next
      next3 isnot ""    // is not empty
      next3 isnot next1 // it gets a new value
      next3 isnot next2 // newer value
    }
  }
}
