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

import scala.collection.immutable.ArraySeq
import scala.concurrent.{ExecutionContext, Future}

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalacheck.Gen
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import org.alephium.api.ApiError
import org.alephium.explorer.{AlephiumSpec, GroupSetting, Hash, HttpJsonSupport}
import org.alephium.explorer.GenApiModel._
import org.alephium.explorer.Generators._
import org.alephium.explorer.api.model._
import org.alephium.explorer.cache.TransactionCache
import org.alephium.explorer.persistence.DatabaseFixtureForEach
import org.alephium.explorer.service.TransactionService
import org.alephium.protocol.model.{TokenId, TransactionId}
import org.alephium.util.U256

@SuppressWarnings(Array("org.wartremover.warts.Var"))
class AddressServerSpec()
    extends AlephiumSpec
    with AkkaDecodeFailureHandler
    with DatabaseFixtureForEach
    with ScalatestRouteTest
    with HttpJsonSupport {

  "validate and forward `txLimit` query param " in new Fixture {
    var testLimit = 0
    override lazy val transactionService = new EmptyTransactionService {
      override def getTransactionsByAddress(address: Address, pagination: Pagination)(
          implicit ec: ExecutionContext,
          dc: DatabaseConfig[PostgresProfile]): Future[ArraySeq[Transaction]] = {
        testLimit = pagination.limit
        Future.successful(ArraySeq.empty)
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

  "check if addresses are active" in new Fixture {
    forAll(addressGen) {
      case (address) =>
        val entity = HttpEntity(ContentTypes.`application/json`, s"""["$address"]""")
        Post(s"/addresses-active", entity) ~> server.route ~> check {
          responseAs[ArraySeq[Boolean]] is ArraySeq(true)
        }
    }
  }

  "respect the max number of addresses" in new Fixture {
    forAll(addressGen) {
      case (address) =>
        val size = groupSetting.groupNum * 20

        val jsonOk   = s"[${ArraySeq.fill(size)(s""""$address"""").mkString(",")}]"
        val entityOk = HttpEntity(ContentTypes.`application/json`, jsonOk)
        Post(s"/addresses-active", entityOk) ~> server.route ~> check {
          status is StatusCodes.OK
        }

        val jsonFail   = s"[${ArraySeq.fill(size + 1)(s""""$address"""").mkString(",")}]"
        val entityFail = HttpEntity(ContentTypes.`application/json`, jsonFail)
        Post(s"/addresses-active", entityFail) ~> server.route ~> check {
          status is StatusCodes.BadRequest
          responseAs[ApiError.BadRequest] is ApiError.BadRequest(
            s"Invalid value for: body (expected size of value to be less than or equal to $size, but was ${size + 1})")
        }
    }
  }

  "list unconfirmed transactions for a given address" in new Fixture {
    forAll(addressGen) {
      case (address) =>
        Get(s"/addresses/${address}/unconfirmed-transactions") ~> server.route ~> check {
          responseAs[ArraySeq[UnconfirmedTransaction]] is ArraySeq(unconfirmedTx)
        }
    }
  }

  trait Fixture {

    implicit val groupSetting: GroupSetting = groupSettingGen.sample.get

    lazy val transactionService = new EmptyTransactionService {}

    lazy val server = new AddressServer(transactionService)

    val unconfirmedTx = utransactionGen.sample.get

    trait EmptyTransactionService extends TransactionService {
      override def getTransaction(transactionHash: TransactionId)(
          implicit ec: ExecutionContext,
          dc: DatabaseConfig[PostgresProfile]): Future[Option[TransactionLike]] =
        Future.successful(None)

      override def getOutputRefTransaction(key: Hash)(
          implicit ec: ExecutionContext,
          dc: DatabaseConfig[PostgresProfile]): Future[Option[Transaction]] =
        Future.successful(None)

      override def getTransactionsNumberByAddress(address: Address)(
          implicit ec: ExecutionContext,
          dc: DatabaseConfig[PostgresProfile]): Future[Int] =
        Future.successful(0)

      override def getTransactionsByAddressSQL(address: Address, pagination: Pagination)(
          implicit ec: ExecutionContext,
          dc: DatabaseConfig[PostgresProfile]): Future[ArraySeq[Transaction]] =
        Future.successful(ArraySeq.empty)

      override def listUnconfirmedTransactionsByAddress(address: Address)(
          implicit ec: ExecutionContext,
          dc: DatabaseConfig[PostgresProfile]): Future[ArraySeq[UnconfirmedTransaction]] = {
        Future.successful(ArraySeq(unconfirmedTx))
      }

      override def getTransactionsByAddress(address: Address, pagination: Pagination)(
          implicit ec: ExecutionContext,
          dc: DatabaseConfig[PostgresProfile]): Future[ArraySeq[Transaction]] =
        Future.successful(ArraySeq.empty)

      override def getBalance(address: Address)(
          implicit ec: ExecutionContext,
          dc: DatabaseConfig[PostgresProfile]): Future[(U256, U256)] =
        Future.successful((U256.Zero, U256.Zero))

      def getTotalNumber()(implicit cache: TransactionCache): Int =
        0

      def listUnconfirmedTransactions(pagination: Pagination)(
          implicit ec: ExecutionContext,
          dc: DatabaseConfig[PostgresProfile]): Future[ArraySeq[UnconfirmedTransaction]] = ???
      def getTokenBalance(address: Address, token: TokenId)(
          implicit ec: ExecutionContext,
          dc: DatabaseConfig[PostgresProfile]): Future[(U256, U256)] = ???
      def listAddressTokenTransactions(address: Address, token: TokenId, pagination: Pagination)(
          implicit ec: ExecutionContext,
          dc: DatabaseConfig[PostgresProfile]): Future[ArraySeq[Transaction]] = ???
      def listAddressTokens(address: Address)(
          implicit ec: ExecutionContext,
          dc: DatabaseConfig[PostgresProfile]): Future[ArraySeq[TokenId]] = ???
      def listTokenAddresses(token: TokenId, pagination: Pagination)(
          implicit ec: ExecutionContext,
          dc: DatabaseConfig[PostgresProfile]): Future[ArraySeq[Address]] = ???
      def listTokenTransactions(token: TokenId, pagination: Pagination)(
          implicit ec: ExecutionContext,
          dc: DatabaseConfig[PostgresProfile]): Future[ArraySeq[Transaction]] = ???
      def listTokens(pagination: Pagination)(
          implicit ec: ExecutionContext,
          dc: DatabaseConfig[PostgresProfile]): Future[ArraySeq[TokenId]] = ???
      def areAddressesActive(addresses: ArraySeq[Address])(
          implicit ec: ExecutionContext,
          dc: DatabaseConfig[PostgresProfile]): Future[ArraySeq[Boolean]] = {
        Future.successful(ArraySeq(true))
      }
    }
  }
}
