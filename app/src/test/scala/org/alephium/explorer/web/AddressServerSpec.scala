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

import akka.actor.ActorSystem
import akka.stream.scaladsl._
import io.vertx.core.buffer.Buffer
import org.reactivestreams.Publisher
import org.scalacheck.Gen
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile
import sttp.model.StatusCode

import org.alephium.api.ApiError
import org.alephium.explorer._
import org.alephium.explorer.GenApiModel._
import org.alephium.explorer.Generators._
import org.alephium.explorer.HttpFixture._
import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence.DatabaseFixtureForAll
import org.alephium.explorer.service.{EmptyTransactionService, TransactionService}
import org.alephium.util.{Duration, TimeStamp, U256}

@SuppressWarnings(Array("org.wartremover.warts.PlatformDefault", "org.wartremover.warts.Var"))
class AddressServerSpec()
    extends AlephiumActorSpecLike
    with DatabaseFixtureForAll
    with HttpServerFixture {

  implicit val groupSetting: GroupSetting = groupSettingGen.sample.get

  val transactions: ArraySeq[Transaction] =
    ArraySeq.from(Gen.listOfN(30, transactionGen).sample.get.zipWithIndex.map {
      case (tx, index) =>
        tx.copy(timestamp = TimeStamp.now() + Duration.ofDaysUnsafe(index.toLong))
    })
  val unconfirmedTx = utransactionGen.sample.get

  var testLimit = 0

  val transactionService = new EmptyTransactionService {
    override def listUnconfirmedTransactionsByAddress(address: Address)(
        implicit ec: ExecutionContext,
        dc: DatabaseConfig[PostgresProfile]): Future[ArraySeq[UnconfirmedTransaction]] = {
      Future.successful(ArraySeq(unconfirmedTx))
    }

    override def getTransactionsByAddress(address: Address, pagination: Pagination)(
        implicit ec: ExecutionContext,
        dc: DatabaseConfig[PostgresProfile]): Future[ArraySeq[Transaction]] = {
      testLimit = pagination.limit
      Future.successful(ArraySeq.empty)
    }

    override def exportTransactionsByAddress(address: Address,
                                             from: TimeStamp,
                                             to: TimeStamp,
                                             exportType: ExportType,
                                             batchSize: Int)(
        implicit ec: ExecutionContext,
        ac: ActorSystem,
        dc: DatabaseConfig[PostgresProfile]): Publisher[Buffer] = {
      TransactionService.transactionsPublisher(
        address,
        exportType,
        Source(transactions).grouped(batchSize).map(ArraySeq.from)
      )(system)
    }
  }

  val server = new AddressServer(transactionService)

  val routes = server.routes

  "validate and forward `txLimit` query param" in {

    forAll(addressGen, Gen.chooseNum[Int](-10, 120)) {
      case (address, txLimit) =>
        Get(s"/addresses/${address}/transactions?limit=$txLimit") check { response =>
          if (txLimit < 0) {
            response.code is StatusCode.BadRequest
            response.as[ApiError.BadRequest] is ApiError.BadRequest(
              s"Invalid value for: query parameter limit (expected value to be greater than or equal to 0, but got $txLimit)")
          } else if (txLimit > 100) {
            response.code is StatusCode.BadRequest
            response.as[ApiError.BadRequest] is ApiError.BadRequest(
              s"Invalid value for: query parameter limit (expected value to be less than or equal to 100, but got $txLimit)")
          } else {
            response.code is StatusCode.Ok
            testLimit is txLimit
          }
        }

        Get(s"/addresses/${address}/transactions") check { _ =>
          testLimit is 20 //default txLimit
        }
    }
  }

  "get total transactions" in {
    forAll(addressGen) {
      case (address) =>
        Get(s"/addresses/${address}/total-transactions") check { response =>
          response.as[Int] is 0
        }
    }
  }

  "get balance" in {
    forAll(addressGen) {
      case (address) =>
        Get(s"/addresses/${address}/balance") check { response =>
          response.as[AddressBalance] is AddressBalance(U256.Zero, U256.Zero)
        }
    }
  }

  "get address info" in {
    forAll(addressGen) {
      case (address) =>
        Get(s"/addresses/${address}") check { response =>
          response.as[AddressInfo] is AddressInfo(U256.Zero, U256.Zero, 0)
        }
    }
  }

  "check if addresses are active" in {
    forAll(addressGen) {
      case (address) =>
        val entity = s"""["$address"]"""
        Post(s"/addresses-active", Some(entity)) check { response =>
          response.as[ArraySeq[Boolean]] is ArraySeq(true)
        }
    }
  }

  "respect the max number of addresses" in {
    forAll(addressGen)(respectMaxNumberOfAddresses("/addresses-active", _))
  }

  "list unconfirmed transactions for a given address" in {
    forAll(addressGen) {
      case (address) =>
        Get(s"/addresses/${address}/unconfirmed-transactions") check { response =>
          response.as[ArraySeq[UnconfirmedTransaction]] is ArraySeq(unconfirmedTx)
        }
    }
  }

  "/addresses/<address>/export-transactions/" should {
    "handle csv format" in {
      forAll(addressGen) { address =>
        val timestamps = transactions.map(_.timestamp.millis).sorted
        val fromTs     = timestamps.head
        val toTs       = timestamps.last

        Get(s"/addresses/${address}/export-transactions/csv?fromTs=$fromTs&toTs=$toTs") check {
          response =>
            response.body is Right(
              Transaction.csvHeader ++ transactions.map(_.toCsv(address)).mkString
            )
        }
      }
    }
    "restrict time range to 1 year" in {
      forAll(addressGen, Gen.posNum[Long]) {
        case (address, long) =>
          val fromTs = TimeStamp.now().millis
          val toTs   = fromTs + Duration.ofDaysUnsafe(365).millis

          Get(s"/addresses/${address}/export-transactions/csv?fromTs=$fromTs&toTs=$toTs") check {
            response =>
              response.code is StatusCode.Ok
          }

          val toMore = toTs + Duration.ofMillisUnsafe(long).millis
          Get(s"/addresses/${address}/export-transactions/csv?fromTs=$fromTs&toTs=$toMore") check {
            response =>
              response.code is StatusCode.BadRequest
          }
      }
    }
  }

  "getTransactionsByAddresses" should {
    "list transactions for an array of addresses" in {
      forAll(addressGen) { address =>
        Post("/addresses/transactions", s"""["$address"]""") check { response =>
          response.as[ArraySeq[Transaction]] is ArraySeq.empty[Transaction]
        }
      }
    }

    "respect the max number of addresses" in {
      forAll(addressGen)(respectMaxNumberOfAddresses("/addresses/transactions", _))
    }
  }

  def respectMaxNumberOfAddresses(endpoint: String, address: Address) = {
    val size = groupSetting.groupNum * 20

    val jsonOk = s"[${ArraySeq.fill(size)(s""""$address"""").mkString(",")}]"
    Post(endpoint, Some(jsonOk)) check { response =>
      response.code is StatusCode.Ok
    }

    val jsonFail = s"[${ArraySeq.fill(size + 1)(s""""$address"""").mkString(",")}]"
    Post(endpoint, Some(jsonFail)) check { response =>
      response.code is StatusCode.BadRequest
      response.as[ApiError.BadRequest] is ApiError.BadRequest(
        s"Invalid value for: body (expected size of value to be less than or equal to $size, but got ${size + 1})")
    }
  }
}
