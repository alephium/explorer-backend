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

import scala.concurrent.duration._

import org.alephium.explorer.AlephiumSpec
import org.alephium.explorer.benchmark.db.DBExecutor
import org.alephium.explorer.utils.TestUtils._

/**
  * Tests the behaviour of functions implemented in [[WriteBenchmarkState]] using
  *  - [[ByteaWriteState]]
  *  - [[VarcharWriteState]]
  */
class WriteBenchmarkStateSpec extends AlephiumSpec {

  //total number of rows to generate
  val testDataCount = 10

  it should "beforeAll - generate a new empty table" in {
    def doTest[S <: WriteBenchmarkState[_]](state: => S, getRowCount: S => Int): Unit =
      using(state) { state =>
        state.beforeAll()
        //table is empty
        getRowCount(state) is 0
        ()
      }

    doTest[ByteaWriteState](
      new ByteaWriteState(DBExecutor.forH2()),
      state => {
        import state.config.profile.api._
        state.db.runNow(state.tableByteaQuery.length.result, 1.second)
      }
    )

    doTest[VarcharWriteState](
      new VarcharWriteState(DBExecutor.forH2()),
      state => {
        import state.config.profile.api._
        state.db.runNow(state.tableVarcharQuery.length.result, 1.second)
      }
    )
  }

  it should "beforeEach - generate data incrementally" in {
    using(new VarcharWriteState(DBExecutor.forH2())) { state =>
      //invoking next before setNext should return null
      //Option is not used here to avoid the cost of unnecessary
      //memory allocation for accurate benchmarking results
      //scalastyle:off
      state.next shouldBe null
      //scalastyle:off

      state.beforeEach() //generates and set the data
      val next1 = state.next //fetch the set data
      next1 isnot "" //is not empty

      state.beforeEach()
      val next2 = state.next
      next2 isnot "" //is not empty
      next2 isnot next1 //it gets a new value

      state.beforeEach()
      val next3 = state.next
      next3 isnot "" //is not empty
      next3 isnot next1 //it gets a new value
      next3 isnot next2 //newer value
    }
  }
}
