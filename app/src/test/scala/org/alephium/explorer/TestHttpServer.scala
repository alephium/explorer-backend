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
import scala.concurrent._

import akka.testkit.SocketUtil
import io.vertx.core.Vertx
import io.vertx.core.http.HttpServer
import io.vertx.ext.web._
import sttp.tapir.server.vertx.VertxFutureServerInterpreter._

import org.alephium.explorer.web.Server
import org.alephium.util.Service

trait TestHttpServer extends Server with Service {
  def server: Server

  val host: InetAddress = InetAddress.getByName("127.0.0.1")
  val port: Int         = SocketUtil.temporaryLocalPort(SocketUtil.Both)

  private val vertx                                   = Vertx.vertx()
  private val router                                  = Router.router(vertx)
  private val httpBindingPromise: Promise[HttpServer] = Promise()

  private val serverInterpreter = Server.interpreter()
  private val httpServer        = vertx.createHttpServer().requestHandler(router)

  override def endpointsLogic: ArraySeq[EndpointLogic] = server.endpointsLogic

  override def startSelfOnce(): Future[Unit] = {
    endpointsLogic.foreach(endpoint => serverInterpreter.route(endpoint)(router))

    httpServer
      .listen(port, host.getHostAddress)
      .asScala
      .map { httpBinding =>
        logger.info(
          s"${this.getClass.getSimpleName} Server started at http://${host.getHostAddress}:${httpBinding.actualPort()}"
        )
        httpBindingPromise.success(httpBinding)
      }
  }

  override def stopSelfOnce(): Future[Unit] = {
    httpBindingPromise.future.map(_.close().asScala).map(_ => ())
  }

  override def subServices: ArraySeq[Service] = ArraySeq.empty
}
