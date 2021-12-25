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

import org.alephium.explorer.AlephiumSpec
import org.alephium.explorer.benchmark.postgres.util.TestKit._

/**
  * Tests the behaviour of functions implemented in [[WriteBenchStateBase]]
  * to ensure that the functions required by JMH benchmarking and behaving correctly.
  *
  * This tests runs on [[org.alephium.explorer.benchmark.postgres.table.TableVarchar]].
  */
class VarcharWriteBenchStateSpec extends AlephiumSpec {

  //total number of rows to generate
  val testDataCount = 10

  it should "generate data incrementally" in withState(new VarcharWriteBenchState()) { state =>
    //invoking next before setNext should return null
    //Option is not used here to avoid the cost of unnecessary
    //memory allocation for accurate benchmarking results
    //scalastyle:off
    state.next shouldBe null
    //scalastyle:off

    state.setNext() //generates and set the data
    val next1 = state.next //fetch the set data
    next1 isnot "" //is not empty

    state.setNext()
    val next2 = state.next
    next2 isnot "" //is not empty
    next2 isnot next1 //it gets a new value

    state.setNext()
    val next3 = state.next
    next3 isnot "" //is not empty
    next3 isnot next1 //it gets a new value
    next3 isnot next2 //newer value
    ()
  }
}
