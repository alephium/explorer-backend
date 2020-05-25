package org.alephium.explorer

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{StatusCodes, Uri}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.testkit.SocketUtil
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Minutes, Span}

import org.alephium.explorer.api.model.BlockEntry
import org.alephium.util.{AlephiumSpec, AVector, TimeStamp}

class ApplicationSpec() extends AlephiumSpec with ScalatestRouteTest with ScalaFutures {
  override implicit val patienceConfig = PatienceConfig(timeout = Span(1, Minutes))

  val blocks: Seq[BlockEntry] = Seq(BlockEntry("myId", TimeStamp.unsafe(0), 0, 0, 0, AVector.empty))

  val blockFlowPort = SocketUtil.temporaryLocalPort(SocketUtil.Both)
  val blockFlowMock = new ApplicationSpec.BlockFlowServerMock(blockFlowPort, blocks)

  val blockflowBinding = blockFlowMock.server.futureValue

  val app: Application =
    new Application(SocketUtil.temporaryLocalPort(), Uri(s"http://localhost:$blockFlowPort"))

  val routes = app.route

  it should "get a block by its id" in {
    val id = "myId"
    Get(s"/blocks/$id") ~> routes ~> check {
      responseAs[String] is """{"hash":"myId","timestamp":0,"chainFrom":0,"chainTo":0,"height":0,"deps":[]}"""
    }
  }

  it should "generate the documentation" in {
    Get("openapi.yaml") ~> routes ~> check {
      status shouldEqual StatusCodes.OK
    }
  }

  app.stop
}

object ApplicationSpec {

  final case class FetchResponse(blocks: Seq[BlockEntry])
  final case class Result[A: Codec](result: A)

  implicit val fetchResponseCodec: Codec[FetchResponse] = deriveCodec[FetchResponse]
  implicit def resultCodec[A: Codec]: Codec[Result[A]]  = deriveCodec[Result[A]]

  class BlockFlowServerMock(port: Int, blocks: Seq[BlockEntry])(implicit system: ActorSystem)
      extends FailFastCirceSupport {
    val routes: Route =
      post {
        complete(Result(FetchResponse(blocks)))
      }
    val server = Http().bindAndHandle(routes, "localhost", port)
  }
}
