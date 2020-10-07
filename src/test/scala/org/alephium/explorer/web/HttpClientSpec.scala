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

import scala.concurrent.ExecutionContext

import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpEntity, HttpMethods, HttpRequest, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.OverflowStrategy
import akka.testkit.SocketUtil
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Minutes, Span}

import org.alephium.util.AlephiumActorSpec

class HttpClientSpec extends AlephiumActorSpec("HttpClientSpec") with ScalaFutures {
  import HttpClientSpec._
  override implicit val patienceConfig = PatienceConfig(timeout = Span(1, Minutes))

  it should "correctly call and deserialize from an external server" in new Fixture {
    client
      .request[Foo](HttpRequest(uri = s"http://localhost:${port}"))
      .futureValue is Right(Foo("foo", 1))

    client
      .request[Foo](HttpRequest(method = HttpMethods.POST, uri = s"http://localhost:${port}"))
      .futureValue is Right(Foo("foo", 1))

    client
      .request[Int](HttpRequest(uri = s"http://localhost:${port}"))
      .futureValue is Left(
      """Cannot decode json: {"foo":"foo","bar":1} as a Int. Error: DecodingFailure(Int, List())""")

    client
      .request[Foo](HttpRequest(uri = s"http://localhost:${port}/nojson"))
      .futureValue is Left("none/none content type not supported")

    client
      .request[Foo](HttpRequest(uri = s"http://localhost:${port}/badcode"))
      .futureValue is Left(
      s"HttpRequest(HttpMethod(GET),http://localhost:${port}/badcode,List(),HttpEntity.Strict(none/none,0 bytes total),HttpProtocol(HTTP/1.1)) failed with code 404 Not Found")
  }

  trait Fixture extends FailFastCirceSupport {
    implicit val executionContext: ExecutionContext = system.dispatcher

    val foo                = Foo("foo", 1)
    val client: HttpClient = HttpClient(bufferSize = 32, OverflowStrategy.dropTail)
    val routes: Route =
      pathSingleSlash {
        get(complete(foo)) ~
          post((complete(foo)))
      } ~ path("nojson") {
        get(complete(HttpResponse(StatusCodes.OK, entity = HttpEntity.Empty)))
      } ~ path("badcode") {
        get(complete(HttpResponse(StatusCodes.NotFound)))
      }

    val port   = SocketUtil.temporaryLocalPort(SocketUtil.Both)
    val server = Http().bindAndHandle(routes, "localhost", port)
  }
}

object HttpClientSpec {
  final case class Foo(foo: String, bar: Int)
  implicit val fooCodec: Codec[Foo] = deriveCodec[Foo]
}
