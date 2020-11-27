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

package org.alephium.explorer.web

import scala.concurrent.Future

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import org.scalacheck.Gen

import org.alephium.explorer.{AlephiumSpec, Generators}
import org.alephium.explorer.api.ApiError
import org.alephium.explorer.api.model.{Address, Transaction}
import org.alephium.explorer.service.TransactionService

@SuppressWarnings(Array("org.wartremover.warts.Var"))
class AddressServerSpec()
    extends AlephiumSpec
    with AkkaDecodeFailureHandler
    with Generators
    with ScalatestRouteTest
    with FailFastCirceSupport {

  it should "validate and forward `txLimit` query param " in new Fixture {
    var testLimit = 0
    val transactionService = new EmptyTransactionService {
      override def getTransactionsByAddress(address: Address,
                                            txLimit: Int): Future[Seq[Transaction]] = {
        testLimit = txLimit
        Future.successful(Seq.empty)
      }
    }

    val server = new AddressServer(transactionService)

    forAll(addressGen, Gen.chooseNum[Int](-10, 120)) {
      case (address, txLimit) =>
        Get(s"/addresses/${address}?tx-limit=$txLimit") ~> server.route ~> check {
          if (txLimit <= 0) {
            status is StatusCodes.BadRequest
            responseAs[ApiError.BadRequest] is ApiError.BadRequest(
              s"Invalid value for: query parameter tx-limit (expected value to be greater than or equal to 1, but was $txLimit)")
          } else if (txLimit > 100) {
            status is StatusCodes.BadRequest
            responseAs[ApiError.BadRequest] is ApiError.BadRequest(
              s"Invalid value for: query parameter tx-limit (expected value to be less than or equal to 100, but was $txLimit)")
          } else {
            status is StatusCodes.OK
            testLimit is txLimit
          }
        }

        Get(s"/addresses/${address}") ~> server.route ~> check {
          testLimit is 20 //default txLimit
        }
    }
  }

  trait Fixture {

    trait EmptyTransactionService extends TransactionService {
      override def getTransaction(transactionHash: Transaction.Hash): Future[Option[Transaction]] =
        Future.successful(None)
      override def getTransactionsByAddress(address: Address,
                                            txLimit: Int): Future[Seq[Transaction]] =
        Future.successful(Seq.empty)
      override def getBalance(address: Address): Future[Double] = Future.successful(0.0)
    }
  }
}
