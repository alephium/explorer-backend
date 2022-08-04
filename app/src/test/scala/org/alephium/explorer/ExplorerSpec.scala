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
import scala.io.{Codec, Source}
import scala.jdk.CollectionConverters._

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import akka.testkit.SocketUtil
import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import de.heikoseeberger.akkahttpupickle.UpickleCustomizationSupport
import org.scalacheck.Gen
import org.scalatest.Assertion
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Minutes, Span}
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import org.alephium.api.{model, ApiError, ApiModelCodec}
import org.alephium.explorer.GenApiModel._
import org.alephium.explorer.Generators._
import org.alephium.explorer.api.model._
import org.alephium.explorer.config.ExplorerConfig
import org.alephium.explorer.persistence.DatabaseFixture
import org.alephium.explorer.persistence.model.BlockEntity
import org.alephium.explorer.service.BlockFlowClient
import org.alephium.explorer.util.TestUtils._
import org.alephium.json.Json
import org.alephium.json.Json._
import org.alephium.protocol.model.{CliqueId, NetworkId}
import org.alephium.util.{AVector, Hex, TimeStamp, U256}

trait ExplorerSpec
    extends AlephiumSpec
    with ScalatestRouteTest
    with ScalaFutures
    with Eventually
    with UpickleCustomizationSupport {

  override type Api = Json.type
  override def api: Api = Json

  override implicit val patienceConfig = PatienceConfig(timeout = Span(1, Minutes))
  implicit val defaultTimeout          = RouteTestTimeout(5.seconds)

  implicit val groupSetting: GroupSetting = groupSettingGen.sample.get

  val networkId: NetworkId = NetworkId.AlephiumDevNet

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
    new ExplorerSpec.BlockFlowServerMock(localhost, blockFlowPort, blockflow, networkId)

  val blockflowBinding = blockFlowMock.server.futureValue

  def createApp(readOnly: Boolean): ExplorerState = {
    implicit val databaseConfig: DatabaseConfig[PostgresProfile] =
      DatabaseFixture.createDatabaseConfig()

    DatabaseFixture.dropCreateTables()

    val explorerPort = SocketUtil.temporaryLocalPort(SocketUtil.Both)
    @SuppressWarnings(Array("org.wartremover.warts.AnyVal"))
    implicit val explorerConfig: ExplorerConfig = ExplorerConfig.load(
      ConfigFactory
        .parseMap(Map(
          ("alephium.explorer.read-only", readOnly),
          ("alephium.explorer.port", explorerPort),
          ("alephium.blockflow.port", blockFlowPort),
          ("alephium.blockflow.network-id", networkId.id),
          ("alephium.blockflow.group-num", groupSetting.groupNum)
        ).view.mapValues(ConfigValueFactory.fromAnyRef).toMap.asJava)
        .withFallback(DatabaseFixture.config))

    if (readOnly) {
      ExplorerState.ReadOnly()
    } else {
      ExplorerState.ReadWrite()
    }
  }
  @SuppressWarnings(Array("org.wartremover.warts.ThreadSleep"))
  def initApp(app: ExplorerState): Assertion = {
    app.start().futureValue
    //let it sync
    eventually(app.blockCache.getMainChainBlockCount() is blocks.size)
  }

  def app: ExplorerState
  //scalastyle:off null
  lazy val routes: Route = app.akkaHttpServer.routes
  //scalastyle:on null

  "get a block by its id" in {
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

  "get block's transactions" in {
    forAll(Gen.oneOf(blocks)) { block =>
      Get(s"/blocks/${block.hash.value.toHexString}/transactions") ~> routes ~> check {
        val txs = responseAs[Seq[Transaction]]
        txs.sizeIs > 0 is true
        txs.size is block.transactions.size
        txs.foreach(tx => block.transactions.contains(tx))
      }
    }
  }

  "list blocks" in {
    forAll(Gen.choose(1, 3), Gen.choose(2, 4)) {
      case (page, limit) =>
        Get(s"/blocks?page=$page&limit=$limit") ~> routes ~> check {
          val offset = page - 1
          //filter `blocks by the same timestamp as the query for better assertion`
          val drop           = offset * limit
          val expectedBlocks = blocks.sortBy(_.timestamp).reverse.slice(drop, drop + limit)
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

  "get a transaction by its id" in {
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

  "get address' info" in {
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
                .map(_.attoAlphAmount)
                .fold(U256.Zero)(_ addUnsafe _))
            .fold(U256.Zero)(_ addUnsafe _)

        val res = responseAs[AddressInfo]

        res.txNumber is expectedTransactions.size
        res.balance is expectedBalance
      }
    }
  }

  "get all address' transactions" in {
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

  "generate the documentation" in {
    Get("/docs") ~> routes ~> check {
      status is StatusCodes.PermanentRedirect
    }
    Get("/docs/explorer-backend-openapi.json") ~> routes ~> check {
      status is StatusCodes.OK

      val openApiFile =
        using(
          Source.fromResource("explorer-backend-openapi.json")(Codec.UTF8)
        )(
          _.getLines().toList
            .mkString("\n")
            //updating address discovery gap limit according to group number
            .replace(""""maxItems": 80""", s""""maxItems": ${groupSetting.groupNum * 20}"""))

      val expectedOpenapi =
        read[ujson.Value](openApiFile)

      val openapi =
        read[ujson.Value](
          response.entity
            .toStrict(1.seconds)
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

object ExplorerSpec {

  class BlockFlowServerMock(
      address: InetAddress,
      port: Int,
      blockflow: Seq[Seq[model.BlockEntry]],
      networkId: NetworkId)(implicit groupSetting: GroupSetting, system: ActorSystem)
      extends ApiModelCodec
      with UpickleCustomizationSupport {

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
      }))

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
          complete(model.ChainParams(networkId, 18, groupSetting.groupNum, groupSetting.groupNum))
        }

    val server = Http().newServerAt(address.getHostAddress, port).bindFlow(routes)
  }
}

class ExplorerReadOnlySpec extends ExplorerSpec {
  override lazy val app: ExplorerState = createApp(true)
  override def initApp(app: ExplorerState): Assertion = {
    val rwApp: ExplorerState = createApp(false)
    super.initApp(rwApp)
    rwApp.stop().futureValue is ()
    super.initApp(app)
  }
  //TODO how to test that sync services aren't started?
}

class ExplorerReadWriteSpec extends ExplorerSpec {
  override lazy val app: ExplorerState = createApp(false)
}
