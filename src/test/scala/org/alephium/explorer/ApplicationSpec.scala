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
import io.circe.{Decoder, DecodingFailure, HCursor, Json}
import org.scalacheck.Gen
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Minutes, Span}

import org.alephium.explorer.api.ApiError
import org.alephium.explorer.api.model.{BlockEntry, GroupIndex, Height, Transaction}
import org.alephium.explorer.persistence.DatabaseFixture
import org.alephium.explorer.protocol.model.BlockEntryProtocol
import org.alephium.util.{AlephiumSpec, TimeStamp}

class ApplicationSpec()
    extends AlephiumSpec
    with ScalatestRouteTest
    with ScalaFutures
    with DatabaseFixture
    with Generators
    with FailFastCirceSupport {
  override implicit val patienceConfig = PatienceConfig(timeout = Span(1, Minutes))
  implicit val defaultTimeout          = RouteTestTimeout(5.seconds)

  val blockFlow: Seq[Seq[BlockEntryProtocol]] =
    blockFlowGen(groupNum = 4, maxChainSize = 5, startTimestamp = TimeStamp.now).sample.get

  val blocksProtocol: Seq[BlockEntryProtocol] = blockFlow.flatten
  val blocks: Seq[BlockEntry]                 = blocksProtocol.map(_.toApi)

  val transactions: Seq[Transaction] = blocks.flatMap(_.transactions.toArray.toSeq)

  val localhost: InetAddress = InetAddress.getLocalHost

  val blockFlowPort = SocketUtil.temporaryLocalPort(SocketUtil.Both)
  val blockFlowMock =
    new ApplicationSpec.BlockFlowServerMock(localhost, blockFlowPort, blocksProtocol)

  val blockflowBinding = blockFlowMock.server.futureValue

  val app: Application =
    new Application(SocketUtil.temporaryLocalPort(),
                    Uri(s"http://${localhost.getHostAddress}:$blockFlowPort"),
                    databaseConfig)

  //let it sync
  Thread.sleep(2000)

  val routes = app.route

  it should "get a block by its id" in {
    forAll(Gen.oneOf(blocks)) { block =>
      Get(s"/blocks/${block.hash.toHexString}") ~> routes ~> check {
        responseAs[BlockEntry] is block
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
        val res = responseAs[Seq[BlockEntry]]
        expectedBlocks.size is res.size
        expectedBlocks.foreach(block => res.contains(block) is true)
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
      Get(s"/transactions/${transaction.hash.toHexString}") ~> routes ~> check {
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

  it should "generate the documentation" in {
    Get("openapi.yaml") ~> routes ~> check {
      status is StatusCodes.OK
    }
  }

  app.stop
}

object ApplicationSpec {
  import org.alephium.explorer.service.BlockFlowClient._

  implicit val jsonRpcDecoder: Decoder[JsonRpc] = new Decoder[JsonRpc] {
    def decode(c: HCursor, method: String, params: Json): Decoder.Result[JsonRpc] = {
      method match {
        case "get_hashes_at_height" => params.as[GetHashesAtHeight]
        case "get_chain_info"       => params.as[GetChainInfo]
        case "get_block"            => params.as[GetBlock]
        case "self_clique"          => Right(GetSelfClique)
        case _                      => Left(DecodingFailure(s"$method not supported", c.history))
      }
    }
    final def apply(c: HCursor): Decoder.Result[JsonRpc] =
      for {
        method  <- c.downField("method").as[String]
        params  <- c.downField("params").as[Json]
        jsonRpc <- decode(c, method, params)
      } yield jsonRpc
  }

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
          .getOrElse(Height.zero))
    }

    private val peer = PeerAddress(address, Some(port), None)

    val routes: Route =
      post {
        entity(as[JsonRpc]) {
          case GetBlock(hash) =>
            complete(Result(blocks.find(_.hash === hash).get))
          case GetHashesAtHeight(from, to, height) =>
            complete(Result(getHashesAtHeight(from, to, height)))
          case GetChainInfo(from, to) =>
            complete(Result(getChainInfo(from, to)))
          case GetSelfClique =>
            complete(Result(SelfClique(Seq(peer, peer), 2)))
        }
      }

    val server = Http().bindAndHandle(routes, address.getHostAddress, port)
  }
}
