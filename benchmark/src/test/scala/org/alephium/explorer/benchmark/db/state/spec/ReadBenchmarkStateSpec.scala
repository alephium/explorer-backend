// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.benchmark.db.state.spec

import java.util.concurrent.RejectedExecutionException

import scala.concurrent.duration._

import org.alephium.explorer.AlephiumFutureSpec
import org.alephium.explorer.benchmark.db.DBExecutor
import org.alephium.explorer.benchmark.db.state._
import org.alephium.explorer.util.TestUtils._

/** Tests the behaviour of functions implemented in [[ReadBenchmarkState]] using
  *   - [[ByteaReadState]]
  *   - [[VarcharReadState]]
  */
class ReadBenchmarkStateSpec extends AlephiumFutureSpec {

  // total number of rows to generate
  val testDataCount = 10

  "beforeAll - generate & cache test data" in {
    def doTest[D, S <: ReadBenchmarkState[D]](state: => S, getRows: S => Seq[D]): Unit =
      using(state) { state =>
        // create test data
        state.beforeAll()
        state.testDataCount is testDataCount

        // cache should contain all the data
        state.cache.length is testDataCount

        // cached and persisted data should be the same
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

  "beforeEach - fetch next data incrementally" in {
    def doTest(state: => ReadBenchmarkState[_]): Unit =
      using(state) { state =>
        // create test data
        state.beforeAll()

        // read all data one by one
        for (index <- 0 until testDataCount) {
          state.beforeEach()
          state.next is state.cache(index)
        }

        // invoking setNext after all data is read should reset the index and start from the first row
        state.beforeEach()
        state.next is state.cache.head
        ()
      }

    doTest(new ByteaReadState(testDataCount, DBExecutor.forTest()))
    doTest(new VarcharReadState(testDataCount, DBExecutor.forTest()))
  }

  "afterAll - terminate connection" in {
    def doTest(state: => ReadBenchmarkState[_]): Unit =
      using(state) { state =>
        // call beforeAll some data and establish connection
        state.beforeAll()

        // call afterAll to close connection
        state.afterAll()

        // expect rejection
        assertThrows[RejectedExecutionException](state.beforeAll())
        ()
      }

    doTest(new ByteaReadState(testDataCount, DBExecutor.forTest()))
    doTest(new VarcharReadState(testDataCount, DBExecutor.forTest()))
  }
}
