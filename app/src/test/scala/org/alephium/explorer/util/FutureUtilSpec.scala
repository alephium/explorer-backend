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

import scala.concurrent.{ExecutionContext, Future}

import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.wordspec.AnyWordSpec

import org.alephium.explorer.AlephiumSpec._
import org.alephium.explorer.util.FutureUtil._

class FutureUtilSpec extends AnyWordSpec with ScalaFutures with MockFactory {

  implicit val ec: ExecutionContext =
    ExecutionContext.global

  "managed" should {
    "not close resources" when {
      implicit val closeable: AsyncCloseable[Int] =
        mock[AsyncCloseable[Int]] //should will never be invoked

      "body succeeds" in {
        //Body passes so resource does not get closed (its mocked so any calls to it will fail).
        managed(Int.MaxValue)(Future(_)).futureValue is Int.MaxValue
      }

      "nested body succeeds" in {
        //All bodies pass so none of the resources get closed.
        val result =
          managed(1) { resource1 =>
            managed(2) { resource2 =>
              managed(3) { resource3 =>
                Future((resource1, resource2, resource3))
              }
            }
          }

        result.futureValue is ((1, 2, 3))
      }
    }

    "close resources" when {
      "body fails" in {
        implicit val closeable: AsyncCloseable[Int] =
          mock[AsyncCloseable[Int]] //should will never be invoked

        (closeable
          .close(_: Int)(_: ExecutionContext))
          .expects(Int.MaxValue, ec)
          .returns(Future.unit)
          .once() //close will be called once

        val result =
          managed(Int.MaxValue) { _ =>
            Future(throw new Exception("Whoops!"))
          }

        result.failed.futureValue.getMessage should be("Whoops!") //IntelliJ not happy with `is`
      }

      "nested body fails" in {
        implicit val closeable: AsyncCloseable[Int] =
          mock[AsyncCloseable[Int]]

        (closeable
          .close(_: Int)(_: ExecutionContext))
          .expects(1, ec)
          .returns(Future.unit)
          .once() //close will be called once

        val result =
          managed(1) { _ =>
            //first body fails
            Future[Int](throw new Exception("Whoops!")) flatMap { _ =>
              managed(2) { _ =>
                fail("Should never get here")
              }
            }
          }

        result.failed.futureValue.getMessage should be("Whoops!") //IntelliJ not happy with `is`
      }

      "nested body's last body fails" in {
        implicit val closeable: AsyncCloseable[Int] =
          mock[AsyncCloseable[Int]]

        //expect close to be called for all resources in sequence
        inSequence {
          (closeable
            .close(_: Int)(_: ExecutionContext))
            .expects(3, ec) //3 gets closed first
            .returns(Future.unit)

          (closeable
            .close(_: Int)(_: ExecutionContext))
            .expects(2, ec) //2 gets closed
            .returns(Future.unit)

          (closeable
            .close(_: Int)(_: ExecutionContext))
            .expects(1, ec) //1 gets closed last
            .returns(Future.unit)
        }

        val result =
          managed(1) { _ =>
            managed(2) { _ =>
              managed(3) { _ =>
                //final body fails
                Future(throw new Exception("Whoops!"))
              }
            }
          }

        result.failed.futureValue.getMessage should be("Whoops!") //IntelliJ not happy with `is`
      }
    }
  }
}
