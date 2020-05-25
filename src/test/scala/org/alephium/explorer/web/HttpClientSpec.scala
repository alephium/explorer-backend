package org.alephium.explorer.web

import scala.concurrent.ExecutionContext

import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpMethods, HttpRequest}
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
      .futureValue is Left("Int")
  }

  trait Fixture extends FailFastCirceSupport {
    implicit val executionContext: ExecutionContext = system.dispatcher

    val foo                = Foo("foo", 1)
    val client: HttpClient = HttpClient(bufferSize = 32, OverflowStrategy.dropTail)
    val routes: Route =
      get(complete(foo)) ~
        post((complete(foo)))

    val port   = SocketUtil.temporaryLocalPort(SocketUtil.Both)
    val server = Http().bindAndHandle(routes, "localhost", port)
  }
}

object HttpClientSpec {
  final case class Foo(foo: String, bar: Int)
  implicit val fooCodec: Codec[Foo] = deriveCodec[Foo]
}
