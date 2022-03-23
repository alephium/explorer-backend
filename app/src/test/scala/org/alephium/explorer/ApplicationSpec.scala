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

import scala.concurrent.Future
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
import org.alephium.explorer.AlephiumSpec
import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence.DatabaseFixture
import org.alephium.explorer.persistence.model.BlockEntity
import org.alephium.explorer.service.BlockFlowClient
import org.alephium.json.Json
import org.alephium.json.Json._
import org.alephium.protocol.model.{CliqueId, NetworkId}
import org.alephium.util.{AVector, Duration, Hex, TimeStamp, U256}

trait ApplicationSpec
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

  val networkId: NetworkId = NetworkId.AlephiumDevNet

  val blockflowFetchMaxAge: Duration = Duration.ofMinutesUnsafe(30)

  val txLimit = 20

  val blockflow: Seq[Seq[model.BlockEntry]] =
    blockFlowGen(maxChainSize = 5, startTimestamp = TimeStamp.now()).sample.get

  val blocksProtocol: Seq[model.BlockEntry] = blockflow.flatten
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
                                            blockflow,
                                            networkId,
                                            blockflowFetchMaxAge)

  val blockflowBinding = blockFlowMock.server.futureValue

  def createApp(readOnly: Boolean): Application = new Application(
    localhost.getHostAddress,
    SocketUtil.temporaryLocalPort(),
    readOnly,
    Uri(s"http://${localhost.getHostAddress}:$blockFlowPort"),
    groupNum,
    blockflowFetchMaxAge,
    networkId,
    databaseConfig,
    None,
    Duration.ofSecondsUnsafe(5)
  )

  def initApp(app: Application): Unit = {
    app.start.futureValue
    //let it sync once
    eventually(app.blockFlowSyncService.stop().futureValue) is ()
    eventually(app.mempoolSyncService.stop().futureValue) is ()
    ()
  }

  def stopApp(app: Application): Future[Unit] = app.stop

  def app: Application
  val routes = app.server.route

  it should "get a block by its id" in {
    initApp(app)

    forAll(Gen.oneOf(blocks)) { block =>
      Get(s"/blocks/${block.hash.value.toHexString}") ~> routes ~> check {
        val blockResult = responseAs[BlockEntryLite]
        blockResult.hash is block.hash
        blockResult.timestamp is block.timestamp
        blockResult.chainFrom is block.chainFrom
        blockResult.chainTo is block.chainTo
        blockResult.height is block.height
        blockResult.txNumber is block.transactions.size
      }
    }

    forAll(hashGen) { hash =>
      Get(s"/blocks/${hash.toHexString}") ~> routes ~> check {
        status is StatusCodes.NotFound
        responseAs[ApiError.NotFound] is ApiError.NotFound(hash.toHexString)
      }
    }
  }

  it should "get block's transactions" in {
    forAll(Gen.oneOf(blocks)) { block =>
      Get(s"/blocks/${block.hash.value.toHexString}/transactions") ~> routes ~> check {
        val txs = responseAs[Seq[Transaction]]
        txs.size > 0 is true
        txs.size is block.transactions.size
        txs.foreach(tx => block.transactions.contains(tx))
      }
    }
  }

  it should "list blocks" in {
    forAll(Gen.choose(1, 3), Gen.choose(2, 4)) {
      case (page, limit) =>
        Get(s"/blocks?page=$page&limit=$limit") ~> routes ~> check {
          val offset = page - 1
          //filter `blocks by the same timestamp as the query for better assertion`
          val expectedBlocks = blocks.sortBy(_.timestamp).reverse.drop(offset * limit).take(limit)
          val res            = responseAs[ListBlocks]
          val hashes         = res.blocks.map(_.hash)
          expectedBlocks.size is hashes.size
          res.total is blocks.size
        }
    }

    Get(s"/blocks") ~> routes ~> check {
      val res = responseAs[ListBlocks].blocks.map(_.hash)
      res.size is scala.math.min(Pagination.defaultLimit, blocks.size)
    }

    Get(s"/blocks?limit=${blocks.size}") ~> routes ~> check {
      val res = responseAs[ListBlocks].blocks.map(_.hash)
      res.size is blocks.size
      blocks.foreach(block => res.contains(block.hash) is true)
    }

    var blocksPage1: Seq[BlockEntry.Hash] = Seq.empty
    Get(s"/blocks?page=1&limit=${blocks.size / 2 + 1}") ~> routes ~> check {
      blocksPage1 = responseAs[ListBlocks].blocks.map(_.hash)
    }

    var allBlocks: Seq[BlockEntry.Hash] = Seq.empty
    Get(s"/blocks?page=2&limit=${blocks.size / 2 + 1}") ~> routes ~> check {
      val res = responseAs[ListBlocks].blocks.map(_.hash)

      allBlocks = blocksPage1 ++ res

      allBlocks.size is blocks.size
      allBlocks.distinct.size is allBlocks.size

      blocks.foreach(block => allBlocks.contains(block.hash) is true)
    }

    Get(s"/blocks?limit=${blocks.size}&reverse=true") ~> routes ~> check {
      val res = responseAs[ListBlocks].blocks.map(_.hash)

      res is allBlocks.reverse
    }

    Get(s"/blocks?page=0") ~> routes ~> check {
      status is StatusCodes.BadRequest
      responseAs[ApiError.BadRequest] is ApiError.BadRequest(
        "Invalid value for: query parameter page (expected value to be greater than or equal to 1, but was 0)"
      )
    }

    Get(s"/blocks?limit=-1") ~> routes ~> check {
      status is StatusCodes.BadRequest
      responseAs[ApiError.BadRequest] is ApiError.BadRequest(
        "Invalid value for: query parameter limit (expected value to be greater than or equal to 0, but was -1)"
      )
    }

    Get(s"/blocks?limit=-1&page=0") ~> routes ~> check {
      status is StatusCodes.BadRequest
      responseAs[ApiError.BadRequest] is ApiError.BadRequest(
        "Invalid value for: query parameter page (expected value to be greater than or equal to 1, but was 0)"
      )
    }
  }

  it should "get a transaction by its id" in {
    forAll(Gen.oneOf(transactions)) { transaction =>
      Get(s"/transactions/${transaction.hash.value.toHexString}") ~> routes ~> check {
        //TODO Validate full transaction when we have a valid blockchain generator
        responseAs[Transaction].hash is transaction.hash
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

        res.txNumber is expectedTransactions.size
        res.balance is expectedBalance
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
        expectedTransactions.foreach(transaction =>
          res.map(_.hash).contains(transaction.hash) is true)
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
}

object ApplicationSpec {

  class BlockFlowServerMock(_groupNum: Int,
                            address: InetAddress,
                            port: Int,
                            blockflow: Seq[Seq[model.BlockEntry]],
                            networkId: NetworkId,
                            val blockflowFetchMaxAge: Duration)(implicit system: ActorSystem)
      extends ApiModelCodec
      with Generators
      with UpickleCustomizationSupport {

    override lazy val groupNum = _groupNum

    val blocks = blockflow.flatten

    override type Api = Json.type
    override def api: Api = Json

    val cliqueId = CliqueId.generate
    def fetchHashesAtHeight(from: GroupIndex,
                            to: GroupIndex,
                            height: Height): model.HashesAtHeight =
      model.HashesAtHeight(AVector.from(blocks.collect {
        case block
            if block.chainFrom === from.value && block.chainTo === to.value && block.height === height.value =>
          block.hash
      }.toSeq))

    def getChainInfo(from: GroupIndex, to: GroupIndex): model.ChainInfo = {
      model.ChainInfo(
        blocks
          .collect {
            case block if block.chainFrom == from.value && block.chainTo === to.value =>
              block.height
          }
          .maxOption
          .getOrElse(Height.genesis.value))
    }

    private val peer = model.PeerAddress(address, port, 0, 0)
    val HashSegment  = Segment.map(raw => BlockHash.unsafe(Hex.unsafe(raw)))
    val routes: Route =
      path("blockflow") {
        parameters("fromTs".as[Long]) { fromTs =>
          parameters("toTs".as[Long]) { toTs =>
            get {
              complete(
                Future.successful(
                  model.FetchResponse(
                    AVector.from(
                      blockflow
                        .map(
                          _.filter(b =>
                            b.timestamp >= TimeStamp.unsafe(fromTs) && b.timestamp <= TimeStamp
                              .unsafe(toTs))
                        )
                        .map(AVector.from(_)))))
              )
            }
          }
        }
      } ~
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
        path("blockflow" / "chain-info") {
          parameters("fromGroup".as[Int]) { from =>
            parameters("toGroup".as[Int]) { to =>
              get {
                complete(getChainInfo(GroupIndex.unsafe(from), GroupIndex.unsafe(to)))
              }
            }
          }
        } ~
        path("transactions" / "unconfirmed") {
          val txs  = Gen.listOfN(5, transactionTemplateProtocolGen).sample.get
          val from = groupIndexGen.sample.get.value
          val to   = groupIndexGen.sample.get.value
          complete(
            Seq(model.UnconfirmedTransactions(from, to, AVector.from(txs)))
          )
        } ~
        path("infos" / "self-clique") {
          complete(model.SelfClique(cliqueId, AVector(peer), true, true))
        } ~
        path("infos" / "chain-params") {
          complete(model.ChainParams(networkId, 18, groupNum, groupNum))
        }

    val server = Http().newServerAt(address.getHostAddress, port).bindFlow(routes)
  }
}

class ReadOnlyApplicationSpec extends ApplicationSpec {
  override def initApp(app: Application): Unit = {
    val rwApp: Application = createApp(false)
    super.initApp(rwApp)
    stopApp(rwApp).futureValue
    app.start.futureValue
  }

  override lazy val app: Application = createApp(true)

  it should "not have started syncing services" in {
    whenReady(app.blockFlowSyncService.stop().failed) { exception =>
      exception is a[IllegalStateException]
    }
    whenReady(app.mempoolSyncService.stop().failed) { exception =>
      exception is a[IllegalStateException]
    }
  }

}

class ReadWriteApplicationSpec extends ApplicationSpec {
  override lazy val app: Application = createApp(false)
}
