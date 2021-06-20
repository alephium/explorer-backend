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
import scala.io.Source

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{StatusCodes, Uri}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import akka.testkit.SocketUtil
import de.heikoseeberger.akkahttpupickle.UpickleCustomizationSupport
import org.scalacheck.Gen
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Minutes, Span}

import org.alephium.api.{ApiError, ApiModelCodec}
import org.alephium.api.model
import org.alephium.api.model.{ChainInfo, HashesAtHeight, PeerAddress, SelfClique}
import org.alephium.explorer.AlephiumSpec
import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence.DatabaseFixture
import org.alephium.explorer.persistence.model.BlockEntity
import org.alephium.explorer.service.BlockFlowClient
import org.alephium.json.Json
import org.alephium.json.Json._
import org.alephium.protocol.model.{CliqueId, NetworkType}
import org.alephium.util.{AVector, Duration, Hex, TimeStamp, U256}

class ApplicationSpec()
    extends AlephiumSpec
    with ScalatestRouteTest
    with ScalaFutures
    with DatabaseFixture
    with Generators
    with Eventually
    with UpickleCustomizationSupport {

  override type Api = Json.type
  override def api: Api = Json

  override implicit val patienceConfig = PatienceConfig(timeout = Span(1, Minutes))
  implicit val defaultTimeout          = RouteTestTimeout(5.seconds)

  val blockflowFetchMaxAge: Duration = Duration.ofMinutesUnsafe(30)

  val txLimit = 20

  val blockFlow: Seq[Seq[model.BlockEntry]] =
    blockFlowGen(maxChainSize = 5, startTimestamp = TimeStamp.now()).sample.get

  val blocksProtocol: Seq[model.BlockEntry] = blockFlow.flatten
  val blockEntities: Seq[BlockEntity]       = blocksProtocol.map(BlockFlowClient.blockProtocolToEntity)

  val blocks: Seq[BlockEntry] = blockEntitiesToBlockEntries(Seq(blockEntities)).flatten

  val transactions: Seq[Transaction] = blocks.flatMap(_.transactions)

  val addresses: Seq[Address] = blocks
    .flatMap(_.transactions.flatMap(_.outputs.map(_.address)))
    .distinct

  val localhost: InetAddress = InetAddress.getByName("127.0.0.1")

  val blockFlowPort = SocketUtil.temporaryLocalPort(SocketUtil.Both)
  val blockFlowMock =
    new ApplicationSpec.BlockFlowServerMock(groupNum,
                                            localhost,
                                            blockFlowPort,
                                            blocksProtocol,
                                            networkType,
                                            blockflowFetchMaxAge)

  val blockflowBinding = blockFlowMock.server.futureValue

  val app: Application =
    new Application(
      localhost.getHostAddress,
      SocketUtil.temporaryLocalPort(),
      Uri(s"http://${localhost.getHostAddress}:$blockFlowPort"),
      groupNum,
      blockflowFetchMaxAge,
      networkType,
      databaseConfig
    )

  app.start.futureValue

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
        responseAs[ApiError.NotFound] is ApiError.NotFound(hash.toHexString)
      }
    }
  }

  it should "list blocks" in {
    val timestamps   = blocks.map(_.timestamp.millis)
    val minTimestamp = timestamps.min
    val maxTimestamp = timestamps.max
    forAll(Gen.choose(minTimestamp, maxTimestamp)) { to =>
      Get(s"/blocks?from-ts=${minTimestamp}&to-ts=${to}") ~> routes ~> check {
        //filter `blocks by the same timestamp as the query for better assertion`
        val expectedBlocks = blocks.filter(block =>
          block.timestamp.millis >= minTimestamp && block.timestamp.millis <= to)
        val res = responseAs[Seq[BlockEntry.Lite]].map(_.hash)
        expectedBlocks.size is res.size
        expectedBlocks.foreach(block => res.contains(block.hash) is true)
      }
    }

    forAll(Gen.choose(minTimestamp, maxTimestamp)) { to =>
      Get(s"/blocks?from-ts=${maxTimestamp + 1}&to-ts=${to}") ~> routes ~> check {
        status is StatusCodes.BadRequest
        responseAs[ApiError.BadRequest] is ApiError.BadRequest(
          "Invalid value (expected value to pass custom validation: `from-ts` must be before `to-ts`, "
            ++ s"but was '(${TimeStamp.unsafe(maxTimestamp + 1)},${TimeStamp.unsafe(to)})')")
      }
    }

    val maxTimeInterval = 10 * 60 * 1000

    forAll(Gen.choose(minTimestamp, maxTimestamp)) { from =>
      val to = from + maxTimeInterval + 1
      Get(s"/blocks?from-ts=${from}&to-ts=${to}") ~> routes ~> check {
        status is StatusCodes.BadRequest
        responseAs[ApiError.BadRequest] is ApiError.BadRequest(
          s"Invalid value (expected value to pass custom validation: maximum interval is 600000ms, "
            ++ s"but was '${scala.math.abs(to - from)}')")
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
        responseAs[ApiError.NotFound] is ApiError.NotFound(hash.toHexString)
      }
    }
  }

  it should "get address' info" in {
    forAll(Gen.oneOf(addresses)) { address =>
      Get(s"/addresses/${address}") ~> routes ~> check {
        val expectedTransactions =
          transactions
            .filter(_.outputs.exists(_.address == address))
            .sorted(Ordering.by((_: Transaction).timestamp))
        val expectedBalance =
          expectedTransactions
            .map(
              _.outputs
                .filter(out => out.spent.isEmpty && out.address == address)
                .map(_.amount)
                .fold(U256.Zero)(_ addUnsafe _))
            .fold(U256.Zero)(_ addUnsafe _)

        val res = responseAs[AddressInfo]

        res.transactions.size is expectedTransactions.take(txLimit).size
        res.balance is expectedBalance
        expectedTransactions
          .take(txLimit)
          .foreach(transaction => res.transactions.contains(transaction) is true)
      }
    }
  }

  it should "get all address' transactions" in {
    forAll(Gen.oneOf(addresses)) { address =>
      Get(s"/addresses/${address}/transactions") ~> routes ~> check {
        val expectedTransactions =
          transactions.filter(_.outputs.exists(_.address == address)).take(txLimit)
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
    Get("/docs/explorer-backend-openapi.json") ~> routes ~> check {
      status is StatusCodes.OK
      val expectedOpenapi =
        read[ujson.Value](
          Source
            .fromFile(Main.getClass.getResource("/explorer-backend-openapi.json").getPath, "UTF-8")
            .getLines()
            .toSeq
            .mkString("\n")
        )

      val openapi =
        read[ujson.Value](
          response.entity
            .toStrict(Duration.ofMinutesUnsafe(1).asScala)
            .futureValue
            .getData
            .utf8String)

      openapi is expectedOpenapi
    }
    Get("/docs/index.html?url=/docs/explorer-backend-openapi.json") ~> routes ~> check {
      status is StatusCodes.OK
    }
  }

  app.stop
}

object ApplicationSpec {

  class BlockFlowServerMock(groupNum: Int,
                            address: InetAddress,
                            port: Int,
                            blocks: Seq[model.BlockEntry],
                            _networkType: NetworkType,
                            val blockflowFetchMaxAge: Duration)(implicit system: ActorSystem)
      extends ApiModelCodec
      with UpickleCustomizationSupport {

    override type Api = Json.type
    override def api: Api = Json

    implicit def networkType: NetworkType = _networkType
    val cliqueId                          = CliqueId.generate
    def fetchHashesAtHeight(from: GroupIndex, to: GroupIndex, height: Height): HashesAtHeight =
      HashesAtHeight(AVector.from(blocks.collect {
        case block
            if block.chainFrom === from.value && block.chainTo === to.value && block.height === height.value =>
          block.hash
      }))

    def getChainInfo(from: GroupIndex, to: GroupIndex): ChainInfo = {
      ChainInfo(
        blocks
          .collect {
            case block if block.chainFrom == from.value && block.chainTo === to.value =>
              block.height
          }
          .maxOption
          .getOrElse(Height.genesis.value))
    }

    private val peer = PeerAddress(address, port, 0, 0)
    val HashSegment  = Segment.map(raw => BlockHash.unsafe(Hex.unsafe(raw)))
    val routes: Route =
      path("blockflow" / "blocks" / HashSegment) { hash =>
        get { complete(blocks.find(_.hash === hash).get) }
      } ~
        path("blockflow" / "hashes") {
          parameters("fromGroup".as[Int]) { from =>
            parameters("toGroup".as[Int]) { to =>
              parameters("height".as[Int]) { height =>
                get {
                  complete(
                    fetchHashesAtHeight(GroupIndex.unsafe(from),
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
          complete(
            SelfClique(cliqueId, networkType, 18, AVector.fill(groupNum)(peer), true, 1, groupNum))
        }

    val server = Http().newServerAt(address.getHostAddress, port).bindFlow(routes)
  }
}
