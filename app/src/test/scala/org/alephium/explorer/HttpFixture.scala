// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer

import scala.collection.immutable.ArraySeq
import scala.concurrent._

import akka.testkit.SocketUtil
import io.vertx.core.Vertx
import io.vertx.core.http.HttpServer
import io.vertx.ext.web._
import org.scalatest.{Assertion, BeforeAndAfterAll, Suite}
import sttp.client3._
import sttp.client3.asynchttpclient.future.AsyncHttpClientFutureBackend
import sttp.model.{Method, Uri}
import sttp.tapir.server.vertx.VertxFutureServerInterpreter._

import org.alephium.explorer.AlephiumFutures
import org.alephium.json.Json._

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
}

@SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
trait HttpFixture {

  type HttpRequest = RequestT[Identity, Either[String, String], Any]

  val backend = AsyncHttpClientFutureBackend()

  def httpRequest[T, R](
      method: Method,
      endpoint: String,
      maybeBody: Option[String] = None
  ): Int => HttpRequest = { port =>
    val request = basicRequest
      .method(method, parseUri(endpoint).port(port))

    val requestWithBody = maybeBody match {
      case Some(body) => request.body(body).contentType("application/json")
      case None       => request
    }

    requestWithBody
  }

  def httpGet(
      endpoint: String,
      maybeBody: Option[String] = None
  ) =
    httpRequest(Method.GET, endpoint, maybeBody)
  def httpPost(
      endpoint: String,
      maybeBody: Option[String] = None
  ) =
    httpRequest(Method.POST, endpoint, maybeBody)
  def httpPut(
      endpoint: String,
      maybeBody: Option[String] = None
  ) =
    httpRequest(Method.PUT, endpoint, maybeBody)

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
  ): Response[Either[String, String]] = {
    httpPost(endpoint, maybeBody)(port).send(backend).futureValue
  }

  def Post(endpoint: String): Response[Either[String, String]] =
    Post(endpoint, None)

  def Post(endpoint: String, body: String): Response[Either[String, String]] =
    Post(endpoint, Some(body))

  def Put(endpoint: String, body: String) = {
    httpPut(endpoint, Some(body))(port).send(backend).futureValue
  }

  def Delete(endpoint: String, body: String) = {
    httpRequest(Method.DELETE, endpoint, Some(body))(port)
      .send(backend)
      .futureValue
  }

  def Get(
      endpoint: String,
      otherPort: Int = port,
      maybeBody: Option[String] = None
  ): Response[Either[String, String]] = {
    httpGet(endpoint, maybeBody = maybeBody)(otherPort)
      .send(backend)
      .futureValue
  }
}

@SuppressWarnings(
  Array("org.wartremover.warts.DefaultArguments", "org.wartremover.warts.NonUnitStatements")
)
trait HttpServerFixture extends HttpRouteFixture {
  this: Suite =>
  def routes: ArraySeq[Router => Route]
  val port: Int = SocketUtil.temporaryLocalPort(SocketUtil.Both)

  private val vertx                                   = Vertx.vertx()
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
