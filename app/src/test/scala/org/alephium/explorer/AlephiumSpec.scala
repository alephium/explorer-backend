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
import scala.concurrent.ExecutionContext

import org.scalacheck.Shrink
import org.scalactic.Equality
import org.scalactic.source.Position
import org.scalatest.Assertion
import org.scalatest.concurrent.{Eventually, IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.dsl.ResultOfATypeInvocation
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

trait AlephiumSpec
    extends AnyWordSpec
    with ImplicitConversions
    with ScalaCheckDrivenPropertyChecks
    with Matchers {
  @nowarn implicit protected def noShrink[A]: Shrink[A] = Shrink(_ => Stream.empty)

  // scalastyle:off no.should
  implicit class IsOps[A: Equality](left: A)(implicit pos: Position) {
    // scalastyle:off scalatest-matcher
    def is(right: A): Assertion                             = left shouldEqual right
    def is(right: ResultOfATypeInvocation[_]): Assertion    = left shouldBe right
    def isnot(right: A): Assertion                          = left should not equal right
    def isnot(right: ResultOfATypeInvocation[_]): Assertion = left should not be right
    // scalastyle:on scalatest-matcher
  }

  implicit class IsEOps[A: Equality, L](left: Either[L, A])(implicit pos: Position) {
    def rightValue: A =
      left match {
        case Left(error) => throw new AssertionError(error)
        case Right(a)    => a
      }

    def leftValue: L =
      left match {
        case Left(error) => error
        case Right(a)    => throw new AssertionError(a)
      }

    def isE(right: A): Assertion                             = rightValue shouldEqual right
    def isE(right: ResultOfATypeInvocation[_]): Assertion    = rightValue shouldBe right
    def isnotE(right: A): Assertion                          = rightValue should not equal right
    def isnotE(right: ResultOfATypeInvocation[_]): Assertion = rightValue should not be right
  }
}

trait AlephiumFutures extends ScalaFutures with Eventually with IntegrationPatience {
  implicit def executionContext: ExecutionContext = ExecutionContext.Implicits.global
}

trait AlephiumFutureSpec extends AlephiumSpec with AlephiumFutures
