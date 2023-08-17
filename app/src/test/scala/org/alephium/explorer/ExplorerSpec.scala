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

import scala.collection.immutable.ArraySeq
import scala.concurrent.Future
import scala.io.{Codec, Source}
import scala.jdk.CollectionConverters._

import akka.testkit.SocketUtil
import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import io.vertx.core.Vertx
import io.vertx.ext.web._
import org.scalacheck.Gen
import org.scalatest.Assertion
import org.scalatest.Inspectors
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.time.{Seconds, Span}
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.server.vertx.VertxFutureServerInterpreter._

import org.alephium.api.{model, ApiError, ApiModelCodec}
import org.alephium.api.{alphJsonBody => jsonBody}
import org.alephium.explorer.ConfigDefaults._
import org.alephium.explorer.GenApiModel._
import org.alephium.explorer.GenCoreApi._
import org.alephium.explorer.GenCoreProtocol._
import org.alephium.explorer.Generators._
import org.alephium.explorer.HttpFixture._
import org.alephium.explorer.api._
import org.alephium.explorer.api.model._
import org.alephium.explorer.config.{BootMode, ExplorerConfig}
import org.alephium.explorer.persistence.DatabaseFixture
import org.alephium.explorer.persistence.DatabaseFixtureForAll
import org.alephium.explorer.persistence.model.BlockEntity
import org.alephium.explorer.service.BlockFlowClient
import org.alephium.explorer.util.TestUtils._
import org.alephium.explorer.web._
import org.alephium.json.Json._
import org.alephium.protocol.config.GroupConfig
import org.alephium.protocol.model.{Address, BlockHash, CliqueId, GroupIndex, NetworkId}
import org.alephium.util.{AVector, Hex, TimeStamp, U256}

trait ExplorerSpec
    extends AlephiumActorSpecLike
    with AlephiumFutureSpec
    with DatabaseFixtureForAll
    with HttpRouteFixture {

  implicit override val patienceConfig =
    PatienceConfig(timeout = Span(120, Seconds))

  override val name: String = "ExploreSpec"

  val networkId: NetworkId = NetworkId.AlephiumDevNet

  val txLimit = 20

  val blockflow: ArraySeq[ArraySeq[model.BlockEntry]] =
    blockFlowGen(maxChainSize = 5, startTimestamp = TimeStamp.now()).sample.get

  val blocksProtocol: ArraySeq[model.BlockEntry] = blockflow.flatten
  val blockEntities: ArraySeq[BlockEntity] =
    blocksProtocol.map(BlockFlowClient.blockProtocolToEntity)

  val blocks: ArraySeq[BlockEntry] = blockEntitiesToBlockEntries(ArraySeq(blockEntities)).flatten

  val transactions: ArraySeq[Transaction] = blocks.flatMap(_.transactions)

  val addresses: ArraySeq[Address] = blocks
    .flatMap(_.transactions.flatMap(_.outputs.map(_.address)))
    .distinct

  val localhost: InetAddress = InetAddress.getByName("127.0.0.1")

  val blockFlowPort = SocketUtil.temporaryLocalPort(SocketUtil.Both)
  val blockFlowMock =
    new ExplorerSpec.BlockFlowServerMock(localhost, blockFlowPort, blockflow, networkId)

  val blockflowBinding = blockFlowMock.server

  def createApp(bootMode: BootMode.Readable): ExplorerState = {
    // We create a `databaseConfig` for each `ExplorerState`. If we use
    // the one from `DatabaseFixture`, the connection might get closed by
    // one of the `ExplorerState` and not available anymore for others.
    implicit val databaseConfig: DatabaseConfig[PostgresProfile] =
      DatabaseFixture.createDatabaseConfig(dbName)
    val explorerPort = SocketUtil.temporaryLocalPort(SocketUtil.Both)
    val configValues: Map[String, Any] = Map(
      ("alephium.explorer.boot-mode", bootMode.productPrefix),
      ("alephium.explorer.port", explorerPort),
      ("alephium.blockflow.port", blockFlowPort),
      ("alephium.blockflow.network-id", networkId.id),
      ("alephium.blockflow.group-num", groupSetting.groupNum)
    )
    @SuppressWarnings(Array("org.wartremover.warts.AnyVal"))
    implicit val explorerConfig: ExplorerConfig = ExplorerConfig.load(
      ConfigFactory
        .parseMap(
          configValues.view.mapValues(ConfigValueFactory.fromAnyRef).toMap.asJava
        )
        .withFallback(DatabaseFixture.config(dbName))
    )

    bootMode match {
      case BootMode.ReadOnly  => ExplorerState.ReadOnly()
      case BootMode.ReadWrite => ExplorerState.ReadWrite()
    }
  }

  def initApp(app: ExplorerState): Assertion = {
    app.start().futureValue
    // let it sync
    eventually(app.blockCache.getMainChainBlockCount() is blocks.size)
  }

  def app: ExplorerState

  lazy val port = app.config.port

  "get a block by its id" in {
    initApp(app)

    // forAll(Gen.oneOf(blocks)) { block =>
    val block = Gen.oneOf(blocks).sample.get
    Get(s"/blocks/${block.hash.value.toHexString}") check { response =>
      val blockResult = response.as[BlockEntryLite]
      blockResult.hash is block.hash
      blockResult.timestamp is block.timestamp
      blockResult.chainFrom is block.chainFrom
      blockResult.chainTo is block.chainTo
      blockResult.height is block.height
      blockResult.txNumber is block.transactions.size
      // }
    }

    forAll(hashGen) { hash =>
      Get(s"/blocks/${hash.toHexString}") check { response =>
        response.code is StatusCode.NotFound
        response.as[ApiError.NotFound] is ApiError.NotFound(hash.toHexString)
      }
    }
  }

  "get block's transactions" in {
    forAll(Gen.oneOf(blocks)) { block =>
      Get(s"/blocks/${block.hash.value.toHexString}/transactions") check { response =>
        val txs = response.as[ArraySeq[Transaction]]
        txs.sizeIs > 0 is true
        txs.size is block.transactions.size
        Inspectors.forAll(txs.map(_.hash))(tx => block.transactions.map(_.hash) should contain(tx))
      }
    }
  }

  "list blocks" in {
    forAll(Gen.choose(1, 3), Gen.choose(2, 4)) { case (page, limit) =>
      Get(s"/blocks?page=$page&limit=$limit") check { response =>
        val offset = page - 1
        // filter `blocks by the same timestamp as the query for better assertion`
        val drop           = offset * limit
        val expectedBlocks = blocks.sortBy(_.timestamp).reverse.slice(drop, drop + limit)
        val res            = response.as[ListBlocks]
        val hashes         = res.blocks.map(_.hash)

        expectedBlocks.size is hashes.size

        res.total is blocks.size
      }
    }

    Get(s"/blocks") check { response =>
      val res = response.as[ListBlocks].blocks.map(_.hash)
      res.size is scala.math.min(Pagination.defaultLimit, blocks.size)
    }

    Get(s"/blocks?limit=${blocks.size}") check { response =>
      val res = response.as[ListBlocks].blocks.map(_.hash)
      res.size is blocks.size
      Inspectors.forAll(blocks)(block => res should contain(block.hash))
    }

    var blocksPage1: ArraySeq[BlockHash] = ArraySeq.empty
    Get(s"/blocks?page=1&limit=${blocks.size / 2 + 1}") check { response =>
      blocksPage1 = response.as[ListBlocks].blocks.map(_.hash)
      response.code is StatusCode.Ok
    }

    var allBlocks: ArraySeq[BlockHash] = ArraySeq.empty
    Get(s"/blocks?page=2&limit=${blocks.size / 2 + 1}") check { response =>
      val res = response.as[ListBlocks].blocks.map(_.hash)

      allBlocks = blocksPage1 ++ res

      allBlocks.size is blocks.size
      allBlocks.distinct.size is allBlocks.size

      Inspectors.forAll(blocks)(block => allBlocks should contain(block.hash))
    }

    Get(s"/blocks?limit=${blocks.size}&reverse=true") check { response =>
      val res = response.as[ListBlocks].blocks.map(_.hash)

      res is allBlocks.reverse
    }

    Get(s"/blocks?page=0") check { response =>
      response.code is StatusCode.BadRequest
      response.as[ApiError.BadRequest] is ApiError.BadRequest(
        "Invalid value for: query parameter page (expected value to be greater than or equal to 1, but got 0)"
      )
    }

    Get(s"/blocks?limit=-1") check { response =>
      response.code is StatusCode.BadRequest
      response.as[ApiError.BadRequest] is ApiError.BadRequest(
        "Invalid value for: query parameter limit (expected value to be greater than or equal to 0, but got -1)"
      )
    }

    Get(s"/blocks?limit=-1&page=0") check { response =>
      response.code is StatusCode.BadRequest
      response.as[ApiError.BadRequest] is ApiError.BadRequest(
        "Invalid value for: query parameter page (expected value to be greater than or equal to 1, but got 0)"
      )
    }
  }

  "get a transaction by its id" in {
    forAll(Gen.oneOf(transactions)) { transaction =>
      Get(s"/transactions/${transaction.hash.value.toHexString}") check { response =>
        // TODO Validate full transaction when we have a valid blockchain generator
        response.as[Transaction].hash is transaction.hash
      }
    }

    forAll(hashGen) { hash =>
      Get(s"/transactions/${hash.toHexString}") check { response =>
        response.code is StatusCode.NotFound
        response.as[ApiError.NotFound] is ApiError.NotFound(hash.toHexString)
      }
    }
  }

  "get address' info" in {
    forAll(Gen.oneOf(addresses)) { address =>
      Get(s"/addresses/${address}") check { response =>
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
                .fold(U256.Zero)(_ addUnsafe _)
            )
            .fold(U256.Zero)(_ addUnsafe _)

        val res = response.as[AddressInfo]

        res.txNumber is expectedTransactions.size
        res.balance is expectedBalance
      }
    }
  }

  "get all address' transactions" in {
    forAll(Gen.oneOf(addresses)) { address =>
      Get(s"/addresses/${address}/transactions") check { response =>
        val expectedTransactions =
          transactions.filter(_.outputs.exists(_.address == address)).take(txLimit)
        val res = response.as[ArraySeq[Transaction]]

        res.size is expectedTransactions.size
        Inspectors.forAll(expectedTransactions) { transaction =>
          res.map(_.hash) should contain(transaction.hash)
        }
      }
    }
  }

  "get address tokens with balance" in {
    forAll(Gen.oneOf(addresses)) { address =>
      Get(s"/addresses/${address}/tokens-balance") check { response =>
        val res = response.as[ArraySeq[AddressTokenBalance]]

        val tokens = blocks
          .flatMap(_.transactions.flatMap(_.outputs.filter(_.address == address).flatMap(_.tokens)))
          .flatten
          .distinct

        res.size is tokens.size
      }
    }
  }

  "get all transactions for addresses" in {
    forAll(Gen.someOf(transactions)) { transactions =>
      val limitedAddresses = transactions.flatMap(_.outputs.map(_.address)).take(txLimit)
      val addressesBody = limitedAddresses.map(address => s""""$address"""").mkString("[", ",", "]")

      Post("/addresses/transactions", addressesBody) check { response =>
        val expectedTransactions =
          transactions.filter(_.outputs.exists(limitedAddresses.contains)).take(txLimit)

        val res = response.as[ArraySeq[Transaction]]

        res.size is limitedAddresses.size
        Inspectors.forAll(expectedTransactions) { transaction =>
          res.map(_.hash) should contain(transaction.hash)
        }
      }
    }
  }

  "list token information" in {
    val tokens = blocks
      .flatMap(_.transactions.flatMap(_.outputs.flatMap(_.tokens)))
      .flatten
      .distinct
    val limit =
      if (tokens.sizeIs > Pagination.maxLimit) {
        Pagination.maxLimit
      } else {
        tokens.size
      }
    Get(s"/tokens?limit=${limit}") check { response =>
      val tokensInfos = response.as[ArraySeq[TokenInfo]]
      tokensInfos.size is limit
    }
  }

  "generate the documentation" in {
    Get("/docs") check { response =>
      response.code is StatusCode.Ok
    }
    Get("/docs/explorer-backend-openapi.json") check { response =>
      response.code is StatusCode.Ok

      val openApiFile =
        using(
          Source.fromResource("explorer-backend-openapi.json")(Codec.UTF8)
        )(_.getLines().toList.mkString)

      // `maxItems` is hardcoded on some place and depend on group num in others
      // Previously we were changing the value based on the group setting of the test,
      // but with the hardcoded value it's impossible to differentiante those values,
      // so we remove them from the json.
      val expectedOpenapi =
        ExplorerSpec.removeField("maxItems", read[ujson.Value](openApiFile))

      val openapi =
        ExplorerSpec.removeField("maxItems", response.as[ujson.Value])

      openapi is expectedOpenapi
    }
  }
}

object ExplorerSpec {

  class BlockFlowServerMock(
      address: InetAddress,
      port: Int,
      blockflow: ArraySeq[ArraySeq[model.BlockEntry]],
      networkId: NetworkId
  )(implicit groupSetting: GroupSetting)
      extends ApiModelCodec
      with BaseEndpoint
      with ScalaFutures
      with QueryParams
      with Server
      with IntegrationPatience {

    implicit val groupConfig: GroupConfig = groupSetting.groupConfig
    val blocks                            = blockflow.flatten

    val cliqueId = CliqueId.generate

    private val peer = model.PeerAddress(address, port, 0, 0)

    def fetchHashesAtHeight(
        from: GroupIndex,
        to: GroupIndex,
        height: Height
    ): model.HashesAtHeight =
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
          .getOrElse(Height.genesis.value)
      )
    }

    private val vertx  = Vertx.vertx()
    private val router = Router.router(vertx)

    vertx
      .fileSystem()
      .existsBlocking(
        "META-INF/resources/webjars/swagger-ui/"
      ) // Fix swagger ui being not found on the first call

    val routes: ArraySeq[Router => Route] =
      ArraySeq(
        route(
          baseEndpoint.get
            .in("blockflow")
            .in("blocks")
            .in(path[BlockHash])
            .out(jsonBody[model.BlockEntry])
            .serverLogicSuccess[Future] { hash =>
              Future.successful(blocks.find(_.hash === hash).get)
            }
        ),
        route(
          baseEndpoint.get
            .in("blockflow")
            .in("blocks-with-events")
            .in(path[BlockHash])
            .out(jsonBody[model.BlockAndEvents])
            .serverLogicSuccess[Future] { hash =>
              Future
                .successful(
                  model.BlockAndEvents(
                    blocks.find(_.hash === hash).get,
                    AVector.from(Gen.listOfN(3, contractEventByBlockHash).sample.get)
                  )
                )
            }
        ),
        route(
          baseEndpoint.get
            .in("blockflow")
            .in("blocks")
            .in(timeIntervalQuery)
            .out(jsonBody[model.BlocksPerTimeStampRange])
            .serverLogicSuccess[Future] { timeInterval =>
              Future.successful(
                model.BlocksPerTimeStampRange(
                  AVector.from(
                    blockflow
                      .map(
                        _.filter(b =>
                          b.timestamp >= timeInterval.from && b.timestamp <= timeInterval.to
                        )
                      )
                      .map(AVector.from(_))
                  )
                )
              )
            }
        ),
        route(
          baseEndpoint.get
            .in("blockflow")
            .in("blocks-with-events")
            .in(timeIntervalQuery)
            .out(jsonBody[model.BlocksAndEventsPerTimeStampRange])
            .serverLogicSuccess[Future] { timeInterval =>
              Future.successful(
                model.BlocksAndEventsPerTimeStampRange(
                  AVector.from(
                    blockflow
                      .map(
                        _.filter(b =>
                          b.timestamp >= timeInterval.from && b.timestamp <= timeInterval.to
                        )
                      )
                      .map(blocks =>
                        AVector
                          .from(
                            blocks.map(block =>
                              model.BlockAndEvents(
                                block,
                                AVector.from(Gen.listOfN(3, contractEventByBlockHash).sample.get)
                              )
                            )
                          )
                      )
                  )
                )
              )
            }
        ),
        route(
          baseEndpoint.get
            .in("blockflow")
            .in("hashes")
            .in(query[Int]("fromGroup"))
            .in(query[Int]("toGroup"))
            .in(query[Int]("height"))
            .out(jsonBody[model.HashesAtHeight])
            .serverLogicSuccess[Future] { case (from, to, height) =>
              Future.successful(
                fetchHashesAtHeight(
                  GroupIndex.unsafe(from),
                  GroupIndex.unsafe(to),
                  Height.unsafe(height)
                )
              )
            }
        ),
        route(
          baseEndpoint.get
            .in("blockflow")
            .in("chain-info")
            .in(query[Int]("fromGroup"))
            .in(query[Int]("toGroup"))
            .out(jsonBody[model.ChainInfo])
            .serverLogicSuccess[Future] { case (from, to) =>
              Future.successful(
                getChainInfo(GroupIndex.unsafe(from), GroupIndex.unsafe(to))
              )
            }
        ),
        route(
          baseEndpoint.get
            .in("mempool")
            .in("transactions")
            .out(jsonBody[ArraySeq[model.MempoolTransactions]])
            .serverLogicSuccess[Future] { _ =>
              val txs  = Gen.listOfN(5, transactionTemplateProtocolGen).sample.get
              val from = groupIndexGen.sample.get.value
              val to   = groupIndexGen.sample.get.value
              Future.successful(
                ArraySeq(model.MempoolTransactions(from, to, AVector.from(txs)))
              )
            }
        ),
        route(
          baseEndpoint.get
            .in("infos")
            .in("self-clique")
            .out(jsonBody[model.SelfClique])
            .serverLogicSuccess[Future] { _ =>
              Future.successful(
                model.SelfClique(cliqueId, AVector(peer), true, true)
              )
            }
        ),
        route(
          baseEndpoint.get
            .in("infos")
            .in("chain-params")
            .out(jsonBody[model.ChainParams])
            .serverLogicSuccess[Future] { _ =>
              Future.successful(
                model.ChainParams(networkId, 18, groupSetting.groupNum, groupSetting.groupNum)
              )
            }
        ),
        route(
          baseEndpoint.get
            .in("contracts")
            .in(path[Address.Contract]("address"))
            .in("state")
            .in(query[GroupIndex]("group"))
            .out(jsonBody[model.ContractState])
            .serverLogicSuccess[Future] { case (address, _) =>
              val interfaceId = Gen.option(stdInterfaceIdGen).sample.get
              val idBytes: Option[model.Val] = interfaceId.map(id =>
                model.ValByteVec(Hex.from(s"${BlockFlowClient.interfaceIdPrefix}000${id.id}").get)
              )
              val immFields = idBytes.map(AVector(_)).getOrElse(AVector.empty)
              Future.successful(
                model.ContractState(
                  address,
                  statefulContractGen.sample.get,
                  hashGen.sample.get,
                  None,
                  immFields,
                  AVector.empty,
                  assetStateGen.sample.get
                )
              )
            }
        ),
        route(
          baseEndpoint.get
            .in("contracts")
            .in("multicall-contract")
            .in(jsonBody[model.MultipleCallContract])
            .out(jsonBody[model.MultipleCallContractResult])
            .serverLogicSuccess[Future] { _ =>
              val symbol                                      = valByteVecGen.sample.get
              val name                                        = valByteVecGen.sample.get
              val decimals                                    = valU256Gen.sample.get
              val totalSupply                                 = valU256Gen.sample.get
              val symbolResult: model.CallContractResult      = contractResult(symbol)
              val nameResult: model.CallContractResult        = contractResult(name)
              val decimalsResult: model.CallContractResult    = contractResult(decimals)
              val totalSupplyResult: model.CallContractResult = contractResult(totalSupply)

              val results: AVector[model.CallContractResult] =
                AVector(symbolResult, nameResult, decimalsResult, totalSupplyResult)
              Future.successful(
                model.MultipleCallContractResult(results)
              )
            }
        )
      )

    val server = vertx.createHttpServer().requestHandler(router)

    routes.foreach(route => route(router))

    logger.info(s"Full node listening on ${address.getHostAddress}:$port")
    server.listen(port, address.getHostAddress).asScala.futureValue
  }

  def contractResult(value: model.Val): model.CallContractResult = {
    val result = callContractSucceededGen.sample.get
    result.copy(returns = value +: result.returns)
  }

  def removeField(name: String, json: ujson.Value): ujson.Value = {
    @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
    def rec(json: ujson.Value): ujson.Value = {
      json match {
        case obj: ujson.Obj =>
          ujson.Obj.from(
            obj.value.filterNot { case (key, _) => key == name }.map { case (key, value) =>
              key -> rec(value)
            }
          )

        case arr: ujson.Arr =>
          val newValues = arr.value.map { value =>
            rec(value)
          }
          ujson.Arr.from(newValues)

        case x => x
      }
    }
    json match {
      case ujson.Null => ujson.Null
      case other      => rec(other)
    }
  }
}

class ExplorerReadOnlySpec extends ExplorerSpec {
  override lazy val app: ExplorerState = createApp(BootMode.ReadOnly)
  override def initApp(app: ExplorerState): Assertion = {
    val rwApp: ExplorerState = createApp(BootMode.ReadWrite)
    super.initApp(rwApp)
    rwApp.stop().futureValue is ()
    super.initApp(app)
  }
  // TODO how to test that sync services aren't started?
}

class ExplorerReadWriteSpec extends ExplorerSpec {
  override lazy val app: ExplorerState = createApp(BootMode.ReadWrite)
}
