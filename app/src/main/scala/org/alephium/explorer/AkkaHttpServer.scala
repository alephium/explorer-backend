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

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.StrictLogging

import org.alephium.util.Service

/** Stores AkkaHttp related instances created on boot-up */
class AkkaHttpServer(host: String, port: Int, val routes: Route)(
    implicit val executionContext: ExecutionContext,
    actorSystem: ActorSystem)
    extends Service
    with StrictLogging {

  private val httpBindingPromise: Promise[Http.ServerBinding] = Promise()

  override def startSelfOnce(): Future[Unit] = {
    for {
      httpBinding <- Http().newServerAt(host, port).bindFlow(routes)
      _           <- httpBindingPromise.success(httpBinding).future
    } yield {
      logger.info(s"Listening http request on ${httpBinding.localAddress}")
    }
  }

  /** Stop AkkaHttp server and terminates ActorSystem */
  def stopSelfOnce(): Future[Unit] = {
    for {
      binding <- httpBindingPromise.future
      _       <- binding.unbind().recover(throwable => logger.error("Failed to unbind server", throwable))
    } yield {
      logger.info(s"http unbound")
    }
  }

  override def subServices: ArraySeq[Service] = ArraySeq.empty

}
