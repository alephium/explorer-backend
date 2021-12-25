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

package org.alephium.explorer.benchmark.postgres

import org.scalatest.concurrent.ScalaFutures
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.AlephiumSpec
import org.alephium.explorer.benchmark.CurrentThreadExecutionContext.self
import org.alephium.explorer.benchmark.postgres.util.TestKit._

/**
  * Tests the behaviour of functions implemented in [[ReadBenchStateBase]]
  * to ensure that the functions required by JMH benchmarking and behaving correctly.
  *
  * This tests runs on [[org.alephium.explorer.benchmark.postgres.table.TableBytea]].
  */
class ByteaReadBenchStateSpec extends AlephiumSpec with ScalaFutures {

  //total number of rows to generate
  val testDataCount = 10

  it should "generate data" in withState(new ByteaReadBenchState(testDataCount)) { state =>
    state.generateTestData()
    state.testDataCount is testDataCount
    state.connection.run(state.table.length.result).futureValue is testDataCount
    ()
  }

  it should "fetch next data incrementally" in withState(new ByteaReadBenchState(testDataCount)) {
    state =>
      state.generateTestData()

      val allData = state.connection.run(state.table.result).futureValue

      allData should contain theSameElementsInOrderAs state.generatedTestData

      //read all data one by one
      for (index <- 0 until testDataCount) {
        state.setNext()
        state.next is allData(index)
      }

      //invoking setNext after all data is read should reset the index and start from the first row
      state.setNext()
      state.next is allData.head
      ()
  }
}
