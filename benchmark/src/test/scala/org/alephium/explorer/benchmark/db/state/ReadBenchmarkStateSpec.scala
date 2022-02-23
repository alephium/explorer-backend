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

import java.util.concurrent.RejectedExecutionException

import scala.concurrent.duration._

import org.scalatest.concurrent.ScalaFutures

import org.alephium.explorer.AlephiumSpec
import org.alephium.explorer.benchmark.db.DBExecutor
import org.alephium.explorer.util.TestUtils._

/**
  * Tests the behaviour of functions implemented in [[ReadBenchmarkState]] using
  *   - [[ByteaReadState]]
  *   - [[VarcharReadState]]
  */
class ReadBenchmarkStateSpec extends AlephiumSpec with ScalaFutures {

  //total number of rows to generate
  val testDataCount = 10

  it should "beforeAll - generate & cache test data" in {
    def doTest[D, S <: ReadBenchmarkState[D]](state: => S, getRows: S => Seq[D]): Unit =
      using(state) { state =>
        //create test data
        state.beforeAll()
        state.testDataCount is testDataCount

        //cache should contain all the data
        state.cache.length is testDataCount

        //cached and persisted data should be the same
        getRows(state) should contain theSameElementsAs state.cache
        ()
      }

    doTest[Array[Byte], ByteaReadState](
      new ByteaReadState(testDataCount, DBExecutor.forTest()),
      state => {
        import state.config.profile.api._
        state.db.runNow(state.tableByteaQuery.result, 1.second)
      }
    )

    doTest[String, VarcharReadState](
      new VarcharReadState(testDataCount, DBExecutor.forTest()),
      state => {
        import state.config.profile.api._
        state.db.runNow(state.tableVarcharQuery.result, 1.second)
      }
    )
  }

  it should "beforeEach - fetch next data incrementally" in {
    def doTest(state: => ReadBenchmarkState[_]): Unit =
      using(state) { state =>
        //create test data
        state.beforeAll()

        //read all data one by one
        for (index <- 0 until testDataCount) {
          state.beforeEach()
          state.next is state.cache(index)
        }

        //invoking setNext after all data is read should reset the index and start from the first row
        state.beforeEach()
        state.next is state.cache.head
        ()
      }

    doTest(new ByteaReadState(testDataCount, DBExecutor.forTest()))
    doTest(new VarcharReadState(testDataCount, DBExecutor.forTest()))
  }

  it should "afterAll - terminate connection" in {
    def doTest(state: => ReadBenchmarkState[_]): Unit =
      using(state) { state =>
        //call beforeAll some data and establish connection
        state.beforeAll()

        //call afterAll to close connection
        state.afterAll()

        //expect rejection
        assertThrows[RejectedExecutionException](state.beforeAll())
        ()
      }

    doTest(new ByteaReadState(testDataCount, DBExecutor.forTest()))
    doTest(new VarcharReadState(testDataCount, DBExecutor.forTest()))
  }
}
