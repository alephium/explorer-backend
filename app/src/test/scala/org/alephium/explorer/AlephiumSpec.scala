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

package org.alephium.explorer

import scala.annotation.nowarn

import org.scalacheck.Shrink
import org.scalactic.Equality
import org.scalactic.source.Position
import org.scalatest.Assertion
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.dsl.ResultOfATypeInvocation
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

trait IsMatchers extends Matchers {
  // scalastyle:off no.should
  implicit class IsOps[A: Equality](left: A)(implicit pos: Position) {
    // scalastyle:off scalatest-matcher
    def is(right: A): Assertion                             = left shouldEqual right
    def is(right: ResultOfATypeInvocation[_]): Assertion    = left shouldBe right
    def isnot(right: A): Assertion                          = left should not equal right
    def isnot(right: ResultOfATypeInvocation[_]): Assertion = left should not be right
    // scalastyle:on scalatest-matcher
  }
}

trait AlephiumSpec extends AnyFlatSpecLike with ScalaCheckDrivenPropertyChecks with IsMatchers {
  @nowarn protected implicit def noShrink[A]: Shrink[A] = Shrink(_ => Stream.empty)

}

object AlephiumSpec extends IsMatchers
