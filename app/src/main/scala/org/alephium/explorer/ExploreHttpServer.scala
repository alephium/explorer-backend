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

import com.typesafe.scalalogging.StrictLogging
import io.vertx.core.Vertx
import io.vertx.core.http.{HttpMethod, HttpServer}
import io.vertx.ext.web._
import io.vertx.ext.web.handler.CorsHandler
import sttp.tapir.server.vertx.VertxFutureServerInterpreter._

import org.alephium.util.Service

/** Stores AkkaHttp related instances created on boot-up */
@SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
class ExplorerHttpServer(host: String, port: Int, val routes: ArraySeq[Router => Route])(
    implicit val executionContext: ExecutionContext
) extends Service
    with StrictLogging {

  private var vertx: Vertx = _
  // scalastyle:on magic.number
  private val httpBindingPromise: Promise[HttpServer] = Promise()

  override def startSelfOnce(): Future[Unit] = {
    vertx = Vertx.vertx()

    val router = Router.router(vertx)

    vertx
      .fileSystem()
      .existsBlocking(
        "META-INF/resources/webjars/swagger-ui/"
      ) // Fix swagger ui being not found on the first call

    val server = vertx.createHttpServer().requestHandler(router)

    //scalastyle:off magic.number
    router
      .route()
      .handler(
        CorsHandler
          .create()
          .addRelativeOrigin(".*.")
          .allowedMethod(HttpMethod.GET)
          .allowedMethod(HttpMethod.POST)
          .allowedMethod(HttpMethod.PUT)
          .allowedMethod(HttpMethod.HEAD)
          .allowedMethod(HttpMethod.OPTIONS)
          .allowedHeader("*")
          .allowCredentials(true)
          .maxAgeSeconds(1800)
      )

    routes.foreach(route => route(router))

    for {
      httpBinding <- server.listen(port, host).asScala
    } yield {
      logger.info(s"Listening http request on ${httpBinding.actualPort}")
      httpBindingPromise.success(httpBinding)
    }
  }

  /** Stop AkkaHttp server and terminates ActorSystem */
  def stopSelfOnce(): Future[Unit] = {
    for {
      binding <- httpBindingPromise.future
      _       <- binding.close().asScala
      _       <- vertx.close().asScala
    } yield {
      logger.info(s"http unbound")
    }
  }

  override def subServices: ArraySeq[Service] = ArraySeq.empty
}
