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

import scala.concurrent.{ExecutionContext, Future}

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import de.heikoseeberger.akkahttpupickle.UpickleCustomizationSupport
import org.scalacheck.Gen
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import org.alephium.api.ApiError
import org.alephium.explorer.{AlephiumSpec, Generators, Hash}
import org.alephium.explorer.api.model._
import org.alephium.explorer.cache.TransactionCache
import org.alephium.explorer.persistence.DatabaseFixtureForEach
import org.alephium.explorer.service.TransactionService
import org.alephium.json.Json
import org.alephium.util.U256

@SuppressWarnings(Array("org.wartremover.warts.Var"))
class AddressServerSpec()
    extends AlephiumSpec
    with AkkaDecodeFailureHandler
    with DatabaseFixtureForEach
    with Generators
    with ScalatestRouteTest
    with UpickleCustomizationSupport {
  override type Api = Json.type

  override def api: Api = Json

  "validate and forward `txLimit` query param " in new Fixture {
    var testLimit = 0
    override val transactionService = new EmptyTransactionService {
      override def getTransactionsByAddressSQL(address: Address, pagination: Pagination)(
          implicit ec: ExecutionContext,
          dc: DatabaseConfig[PostgresProfile]): Future[Seq[Transaction]] = {
        testLimit = pagination.limit
        Future.successful(Seq.empty)
      }
    }

    forAll(addressGen, Gen.chooseNum[Int](-10, 120)) {
      case (address, txLimit) =>
        Get(s"/addresses/${address}/transactions?limit=$txLimit") ~> server.route ~> check {
          if (txLimit < 0) {
            status is StatusCodes.BadRequest
            responseAs[ApiError.BadRequest] is ApiError.BadRequest(
              s"Invalid value for: query parameter limit (expected value to be greater than or equal to 0, but was $txLimit)")
          } else if (txLimit > 100) {
            status is StatusCodes.BadRequest
            responseAs[ApiError.BadRequest] is ApiError.BadRequest(
              s"Invalid value for: query parameter limit (expected value to be less than or equal to 100, but was $txLimit)")
          } else {
            status is StatusCodes.OK
            testLimit is txLimit
          }
        }

        Get(s"/addresses/${address}/transactions") ~> server.route ~> check {
          testLimit is 20 //default txLimit
        }
    }
  }

  "get total transactions" in new Fixture {
    forAll(addressGen) {
      case (address) =>
        Get(s"/addresses/${address}/total-transactions") ~> server.route ~> check {
          responseAs[Int] is 0
        }
    }
  }

  "get balance" in new Fixture {
    forAll(addressGen) {
      case (address) =>
        Get(s"/addresses/${address}/balance") ~> server.route ~> check {
          responseAs[AddressBalance] is AddressBalance(U256.Zero, U256.Zero)
        }
    }
  }

  "get address info" in new Fixture {
    forAll(addressGen) {
      case (address) =>
        Get(s"/addresses/${address}") ~> server.route ~> check {
          responseAs[AddressInfo] is AddressInfo(U256.Zero, U256.Zero, 0)
        }
    }
  }

  trait Fixture {

    val transactionService = new EmptyTransactionService {}

    lazy val server = new AddressServer(transactionService)

    trait EmptyTransactionService extends TransactionService {
      override def getTransaction(transactionHash: Transaction.Hash)(
          implicit ec: ExecutionContext,
          dc: DatabaseConfig[PostgresProfile]): Future[Option[TransactionLike]] =
        Future.successful(None)

      override def getOutputRefTransaction(key: Hash)(
          implicit ec: ExecutionContext,
          dc: DatabaseConfig[PostgresProfile]): Future[Option[ConfirmedTransaction]] =
        Future.successful(None)

      override def getTransactionsNumberByAddress(address: Address)(
          implicit ec: ExecutionContext,
          dc: DatabaseConfig[PostgresProfile]): Future[Int] =
        Future.successful(0)

      override def getTransactionsByAddressSQL(address: Address, pagination: Pagination)(
          implicit ec: ExecutionContext,
          dc: DatabaseConfig[PostgresProfile]): Future[Seq[Transaction]] =
        Future.successful(Seq.empty)

      override def getBalance(address: Address)(
          implicit ec: ExecutionContext,
          dc: DatabaseConfig[PostgresProfile]): Future[(U256, U256)] =
        Future.successful((U256.Zero, U256.Zero))

      def getTotalNumber()(implicit cache: TransactionCache): Int =
        0

      def getTokenBalance(address: Address, token: Hash)(
          implicit ec: ExecutionContext,
          dc: DatabaseConfig[PostgresProfile]): Future[(U256, U256)] = ???
      def listAddressTokenTransactions(address: Address, token: Hash, pagination: Pagination)(
          implicit ec: ExecutionContext,
          dc: DatabaseConfig[PostgresProfile]): Future[Seq[Transaction]] = ???
      def listAddressTokens(address: Address)(
          implicit ec: ExecutionContext,
          dc: DatabaseConfig[PostgresProfile]): Future[Seq[Hash]] = ???
      def listTokenAddresses(token: Hash, pagination: Pagination)(
          implicit ec: ExecutionContext,
          dc: DatabaseConfig[PostgresProfile]): Future[Seq[Address]] = ???
      def listTokenTransactions(token: Hash, pagination: Pagination)(
          implicit ec: ExecutionContext,
          dc: DatabaseConfig[PostgresProfile]): Future[Seq[Transaction]] = ???
      def listTokens(pagination: Pagination)(
          implicit ec: ExecutionContext,
          dc: DatabaseConfig[PostgresProfile]): Future[Seq[Hash]] = ???
    }
  }
}
