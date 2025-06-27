// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

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
  // scalastyle:on
}

trait AlephiumFutures extends ScalaFutures with Eventually with IntegrationPatience {
  implicit def executionContext: ExecutionContext = ExecutionContext.Implicits.global
}

trait AlephiumFutureSpec extends AlephiumSpec with AlephiumFutures
