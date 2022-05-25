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

package org.alephium.explorer.util

import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.{ExecutionContext, Future}

import org.scalacheck.Gen
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

import org.alephium.explorer.AlephiumSpec._

class LazyValSpec
    extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with ScalaCheckDrivenPropertyChecks {

  implicit val executionContext: ExecutionContext =
    ExecutionContext.global

  "apply" should {
    "not be initialised" in {
      LazyVal(fail("Oh no!")).isDefined is false
    }
  }

  "get" should {
    "fetch and set the value" when {
      "empty" in {
        val lazyVal = LazyVal(Int.MaxValue)

        //is empty
        lazyVal.isEmpty is true
        lazyVal.isDefined is false

        lazyVal.get() is Int.MaxValue //gets initialised

        //is not empty
        lazyVal.isEmpty is false
        lazyVal.isDefined is true
      }

      "cleared" in {
        val integer = new AtomicInteger()
        val lazyVal = LazyVal(integer.incrementAndGet())

        lazyVal.get() is 1 //initialise

        lazyVal.isEmpty is false
        lazyVal.clear()
        lazyVal.isEmpty is true

        lazyVal.get() is 2 //re-initialise

      }
    }
  }

  "concurrent access" should {
    "not allow concurrent mutation" in {
      forAll(Gen.choose(5, 100)) { threads =>
        val state   = new AtomicInteger()
        val lazyVal = LazyVal(state.incrementAndGet())

        //Assert concurrent access should only allow single mutation
        Future
          .sequence(List.fill(threads)(Future(lazyVal.get())))
          .futureValue should contain only 1
      }
    }
  }

}
