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

import scala.collection.immutable.ArraySeq
import scala.concurrent._

import io.vertx.core.streams.WriteStream
import io.vertx.ext.web.codec.BodyCodec
import io.vertx.core.buffer.Buffer
import akka.testkit.SocketUtil
import io.vertx.core.Vertx
import io.vertx.core.http._
import io.vertx.ext.web._
import io.vertx.ext.web.client._
import org.scalatest.{Assertion, BeforeAndAfterAll, Suite}
//import sttp.client3._
import sttp.client3.asynchttpclient.future.AsyncHttpClientFutureBackend
import sttp.model.{StatusCode, Uri}
import sttp.tapir.server.vertx.VertxFutureServerInterpreter.VertxFutureToScalaFuture

import org.alephium.explorer.AlephiumFutures
import org.alephium.json.Json._

final case class Response[T](body: T, code: StatusCode)

object Response {
  def apply(response: HttpResponse[Buffer]): Response[String] = {
    val body = response.bodyAsString
    println(s"${Console.RED}${Console.BOLD}*** body ***\n\t${Console.RESET}${body}")
    Response(
      if (body == null) "" else body,
      StatusCode.unsafeApply(response.statusCode)
    )
  }
}
object HttpFixture {
  implicit class RichResponse[T](val response: Response[T]) extends AnyVal {
    def check(f: Response[T] => Assertion): Assertion = {
      f(response)
    }
  }

  implicit class RichEitherResponse(val response: Response[Either[String, String]]) extends AnyVal {
    def as[T: Reader]: T = {
      val body = response.body match {
        case Right(r) => r
        case Left(l)  => l
      }
      read[T](body)
    }
  }
  implicit class RichStringResponse(val response: Response[String]) extends AnyVal {
    def as[T: Reader]: T = {
      read[T](response.body)
    }
  }

  implicit class VertxFutureToScalaFuture[A](future: io.vertx.core.Future[A]) {
    def asScala: Future[A] = {
      val promise = Promise[A]()
      future.onComplete { handler =>
        if (handler.succeeded()) {
          promise.success(handler.result())
        } else {
          promise.failure(handler.cause())
        }
      }
      promise.future
    }
  }
}

@SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
trait HttpFixture {

  val vertx: Vertx      = Vertx.vertx()
  val client: WebClient = WebClient.create(vertx);

  //type HttpRequest = RequestT[Identity, Either[String, String], Any]

  val backend = AsyncHttpClientFutureBackend()

  def httpRequest[T, R](
      method: HttpMethod,
      endpoint: String,
      maybeBody: Option[String] = None,
      port: Int
  )(implicit ec: ExecutionContext): Future[Response[String]] = {
    // val request = basicRequest
    //   .method(method, parseUri(endpoint).port(port))

    // val requestWithBody = maybeBody match {
    //   case Some(body) => request.body(body).contentType("application/json")
    //   case None       => request
    // }

    // requestWithBody

    val request = client.request(method, port, "localhost", endpoint)
    (maybeBody match {
      case Some(body) => request.sendBuffer(Buffer.buffer(body)).asScala
      case None       => request.send().asScala
    }).map(Response.apply)
  }

  def streamRequest[T, R](
      endpoint: String,
      port: Int,
      writeStream: WriteStream[Buffer]
  )(implicit ec: ExecutionContext): Future[StatusCode] = {
    val request = client.request(HttpMethod.GET, port, "localhost", endpoint)
    request
      .as(BodyCodec.pipe(writeStream))
      .send()
      .asScala
      .map(res => StatusCode.unsafeApply(res.statusCode))
  }

  def httpGet(
      endpoint: String,
      maybeBody: Option[String] = None,
      port: Int
  )(implicit ec: ExecutionContext) =
    httpRequest(HttpMethod.GET, endpoint, maybeBody, port)
  def httpPost(
      endpoint: String,
      maybeBody: Option[String] = None,
      port: Int
  )(implicit ec: ExecutionContext) =
    httpRequest(HttpMethod.POST, endpoint, maybeBody, port)
  def httpPut(
      endpoint: String,
      maybeBody: Option[String] = None,
      port: Int
  )(implicit ec: ExecutionContext) =
    httpRequest(HttpMethod.PUT, endpoint, maybeBody, port)

// scalastyle:off no.equal
  def parsePath(str: String): (Seq[String], Map[String, String]) = {
    if (str.head != '/') {
      throw new Throwable("Path doesn't start with '/'")
    } else {
      val path = str.split('/')
      if (path.head == "/") {
        // root path
        (Seq.empty, Map.empty)
      } else {
        val base = Seq.from(path.tail)
        if (base.last.contains('?')) {
          val res       = base.last.split('?')
          val rawParams = res.tail.head
          val params = rawParams
            .split('&')
            .map { params =>
              val splited = params.split('=')
              (splited.head, splited.tail.head)
            }
            .toMap

          (base.init.appended(res.head), params)
        } else {
          (base, Map.empty)
        }
      }
    }
  }
// scalastyle:on no.equal

  def parseUri(endpoint: String): Uri = {
    val (path, params) = parsePath(endpoint)
    val base           = Uri("127.0.0.1").withPath(path)
    if (params.isEmpty) {
      base
    } else {
      base.withParams(params)
    }
  }
}

@SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
trait HttpRouteFixture extends HttpFixture with BeforeAndAfterAll with AlephiumFutures {
  this: Suite =>

  def port: Int

  def Post(
      endpoint: String,
      maybeBody: Option[String]
  ): Response[String] = {
    httpPost(endpoint, maybeBody, port).futureValue
  }

  def Post(endpoint: String): Response[String] =
    Post(endpoint, None)

  def Post(endpoint: String, body: String): Response[String] =
    Post(endpoint, Some(body))

  def Put(endpoint: String, body: String) = {
    httpPut(endpoint, Some(body), port).futureValue
  }

  def Delete(endpoint: String, body: String) = {
    httpRequest(HttpMethod.DELETE, endpoint, Some(body), port).futureValue
  }

  def Get(
      endpoint: String,
      otherPort: Int            = port,
      maybeBody: Option[String] = None
  ): Response[String] = {
    httpGet(endpoint, maybeBody = maybeBody, otherPort).futureValue
  }
  def Stream(
      endpoint: String,
      writeStream: WriteStream[Buffer]
  ): Future[StatusCode] = {
    streamRequest(endpoint, port, writeStream)
  }
}

@SuppressWarnings(
  Array("org.wartremover.warts.DefaultArguments", "org.wartremover.warts.NonUnitStatements"))
trait HttpServerFixture extends HttpRouteFixture {
  this: Suite =>
  def routes: ArraySeq[Router => Route]
  val port: Int = SocketUtil.temporaryLocalPort(SocketUtil.Both)

  private val router                                  = Router.router(vertx)
  private val httpBindingPromise: Promise[HttpServer] = Promise()

  private val server = vertx.createHttpServer().requestHandler(router)

  override def beforeAll(): Unit = {
    super.beforeAll()
    routes.foreach(route => route(router))
    server
      .listen(port, "localhost")
      .asScala
      .map { httpBinding =>
        httpBindingPromise.success(httpBinding)
      }
      .futureValue
    ()
  }

  override def afterAll(): Unit = {
    super.afterAll()
    httpBindingPromise.future.map(_.close().asScala).futureValue
    ()
  }
}
