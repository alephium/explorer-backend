// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer

import scala.collection.immutable.ArraySeq
import scala.concurrent._
import scala.util.Success

import com.typesafe.scalalogging.StrictLogging
import io.vertx.core.http.{HttpMethod, HttpServer}
import io.vertx.ext.web._
import io.vertx.ext.web.handler.CorsHandler
import sttp.tapir.server.vertx.VertxFutureServerInterpreter._

import org.alephium.explorer.persistence.Database
import org.alephium.explorer.service.VertxService
import org.alephium.util.Service

/** Stores http related instances created on boot-up */
@SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
class ExplorerHttpServer(
    host: String,
    port: Int,
    val routes: ArraySeq[Router => Route],
    vertxService: VertxService,
    database: Database
)(implicit
    val executionContext: ExecutionContext
) extends Service
    with StrictLogging {

  // scalastyle:on magic.number
  private val httpBindingPromise: Promise[HttpServer] = Promise()

  override def startSelfOnce(): Future[Unit] = {
    val vertx  = vertxService.vertx
    val router = Router.router(vertx)

    vertx
      .fileSystem()
      .existsBlocking(
        "META-INF/resources/webjars/swagger-ui/"
      ) // Fix swagger ui being not found on the first call

    val server = vertx.createHttpServer().requestHandler(router)

    // scalastyle:off magic.number
    router
      .route()
      .handler(
        CorsHandler
          .create()
          .addOriginWithRegex(".*.")
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

  def stopSelfOnce(): Future[Unit] = {
    val closeBinding =
      httpBindingPromise.future.value match {
        case Some(Success(binding)) => binding.close().asScala
        case _                      => Future.unit
      }

    for {
      _ <- closeBinding
    } yield {
      logger.info("http unbound")
    }
  }

  override def subServices: ArraySeq[Service] = ArraySeq(vertxService, database)
}
