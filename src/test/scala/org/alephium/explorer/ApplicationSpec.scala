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

import java.net.InetAddress

import scala.concurrent.duration._

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{StatusCodes, Uri}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import akka.testkit.SocketUtil
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import org.scalacheck.Gen
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Minutes, Span}

import org.alephium.explorer.AlephiumSpec
import org.alephium.explorer.api.ApiError
import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence.DatabaseFixture
import org.alephium.explorer.persistence.model.BlockEntity
import org.alephium.explorer.protocol.model.BlockEntryProtocol
import org.alephium.util.{Hex, TimeStamp}

class ApplicationSpec()
    extends AlephiumSpec
    with ScalatestRouteTest
    with ScalaFutures
    with DatabaseFixture
    with Generators
    with FailFastCirceSupport
    with Eventually {
  override implicit val patienceConfig = PatienceConfig(timeout = Span(1, Minutes))
  implicit val defaultTimeout          = RouteTestTimeout(5.seconds)

  val groupNum: Int = 4
  val blockFlow: Seq[Seq[BlockEntryProtocol]] =
    blockFlowGen(groupNum = groupNum, maxChainSize = 5, startTimestamp = TimeStamp.now()).sample.get

  val blocksProtocol: Seq[BlockEntryProtocol] = blockFlow.flatten
  val blockEntities: Seq[BlockEntity]         = blocksProtocol.map(_.toEntity)

  val blocks: Seq[BlockEntry] = blockEntitiesToBlockEntries(Seq(blockEntities)).flatten

  val transactions: Seq[Transaction] = blocks.flatMap(_.transactions)

  val addresses: Seq[Address] = blocks
    .flatMap(_.transactions.flatMap(_.outputs.map(_.address)))
    .distinct

  val localhost: InetAddress = InetAddress.getLocalHost

  val blockFlowPort = SocketUtil.temporaryLocalPort(SocketUtil.Both)
  val blockFlowMock =
    new ApplicationSpec.BlockFlowServerMock(localhost, blockFlowPort, blocksProtocol)

  val blockflowBinding = blockFlowMock.server.futureValue

  val app: Application =
    new Application(localhost.getHostAddress,
                    SocketUtil.temporaryLocalPort(),
                    Uri(s"http://${localhost.getHostAddress}:$blockFlowPort"),
                    groupNum,
                    databaseConfig)

  //let it sync once
  eventually(app.blockFlowSyncService.stop().futureValue) is ()

  val routes = app.server.route

  it should "get a block by its id" in {
    forAll(Gen.oneOf(blocks)) { block =>
      Get(s"/blocks/${block.hash.value.toHexString}") ~> routes ~> check {
        val blockResult = responseAs[BlockEntry]
        blockResult is block.copy(mainChain = blockResult.mainChain)
      }
    }

    forAll(hashGen) { hash =>
      Get(s"/blocks/${hash.toHexString}") ~> routes ~> check {
        status is StatusCodes.NotFound
        responseAs[ApiError] is ApiError.NotFound(hash.toHexString)
      }
    }
  }

  it should "list blocks" in {
    val timestamps   = blocks.map(_.timestamp.millis)
    val minTimestamp = timestamps.min
    val maxTimestamp = timestamps.max
    forAll(Gen.choose(minTimestamp, maxTimestamp)) { to =>
      Get(s"/blocks?fromTs=${minTimestamp}&toTs=${to}") ~> routes ~> check {
        //filter `blocks by the same timestamp as the query for better assertion`
        val expectedBlocks = blocks.filter(block =>
          block.timestamp.millis >= minTimestamp && block.timestamp.millis <= to)
        val res = responseAs[Seq[BlockEntry.Lite]].map(_.hash)
        expectedBlocks.size is res.size
        expectedBlocks.foreach(block => res.contains(block.hash) is true)
      }
    }

    forAll(Gen.choose(minTimestamp, maxTimestamp)) { to =>
      Get(s"/blocks?fromTs=${maxTimestamp + 1}&toTs=${to}") ~> routes ~> check {
        status is StatusCodes.BadRequest
        responseAs[ApiError] is ApiError.BadRequest(
          "Invalid value (expected value to pass custom validation: `fromTs` must be before `toTs`, "
            ++ s"but was '(${TimeStamp.unsafe(maxTimestamp + 1)},${TimeStamp.unsafe(to)})')")
      }
    }
  }

  it should "get a transaction by its id" in {
    forAll(Gen.oneOf(transactions)) { transaction =>
      Get(s"/transactions/${transaction.hash.value.toHexString}") ~> routes ~> check {
        responseAs[Transaction] is transaction
      }
    }

    forAll(hashGen) { hash =>
      Get(s"/transactions/${hash.toHexString}") ~> routes ~> check {
        status is StatusCodes.NotFound
        responseAs[ApiError] is ApiError.NotFound(hash.toHexString)
      }
    }
  }

  it should "get address' info" in {
    forAll(Gen.oneOf(addresses)) { address =>
      Get(s"/addresses/${address}") ~> routes ~> check {
        val expectedTransactions =
          transactions.filter(_.outputs.exists(_.address == address))
        val expectedBalance =
          expectedTransactions
            .map(
              _.outputs
                .filter(out => out.spent.isEmpty && out.address == address)
                .map(_.amount)
                .sum)
            .sum

        val res = responseAs[AddressInfo]

        res.balance is expectedBalance
        res.transactions.size is expectedTransactions.size
        expectedTransactions.foreach(transaction => res.transactions.contains(transaction) is true)
      }
    }
  }

  it should "get all address' transactions" in {
    forAll(Gen.oneOf(addresses)) { address =>
      Get(s"/addresses/${address}/transactions") ~> routes ~> check {
        val expectedTransactions =
          transactions.filter(_.outputs.exists(_.address == address))
        val res = responseAs[Seq[Transaction]]

        res.size is expectedTransactions.size
        expectedTransactions.foreach(transaction => res.contains(transaction) is true)
      }
    }
  }

  it should "generate the documentation" in {
    Get("/docs") ~> routes ~> check {
      status is StatusCodes.PermanentRedirect
    }
    Get("/docs/openapi.yaml") ~> routes ~> check {
      status is StatusCodes.OK
    }
    Get("/docs/index.html?url=/docs/openapi.yaml") ~> routes ~> check {
      status is StatusCodes.OK
    }
  }

  app.stop
}

object ApplicationSpec {
  import org.alephium.explorer.service.BlockFlowClient._

  class BlockFlowServerMock(address: InetAddress, port: Int, blocks: Seq[BlockEntryProtocol])(
      implicit system: ActorSystem)
      extends FailFastCirceSupport {
    def getHashesAtHeight(from: GroupIndex, to: GroupIndex, height: Height): HashesAtHeight =
      HashesAtHeight(blocks.collect {
        case block if block.chainFrom === from && block.chainTo === to && block.height === height =>
          block.hash
      })

    def getChainInfo(from: GroupIndex, to: GroupIndex): ChainInfo = {
      ChainInfo(
        blocks
          .collect { case block if block.chainFrom == from && block.chainTo === to => block.height }
          .maxOption
          .getOrElse(Height.genesis))
    }

    private val peer = PeerAddress(address, Some(port), None)
    val HashSegment  = Segment.map(raw => Hash.unsafe(Hex.unsafe(raw)))
    val routes: Route =
      path("blockflow" / "blocks" / HashSegment) { hash =>
        get { complete(blocks.find(_.hash === (new BlockEntry.Hash(hash))).get) }
      } ~
        path("blockflow" / "hashes") {
          parameters("fromGroup".as[Int]) { from =>
            parameters("toGroup".as[Int]) { to =>
              parameters("height".as[Int]) { height =>
                get {
                  complete(
                    getHashesAtHeight(GroupIndex.unsafe(from),
                                      GroupIndex.unsafe(to),
                                      Height.unsafe(height)))
                }
              }
            }
          }
        } ~
        path("blockflow" / "chains") {
          parameters("fromGroup".as[Int]) { from =>
            parameters("toGroup".as[Int]) { to =>
              get {
                complete(getChainInfo(GroupIndex.unsafe(from), GroupIndex.unsafe(to)))
              }
            }
          }
        } ~
        path("infos" / "self-clique") {
          complete(SelfClique(Seq(peer, peer), 2))
        }

    val server = Http().bindAndHandle(routes, address.getHostAddress, port)
  }
}
