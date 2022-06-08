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

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

import akka.actor.ActorSystem
import com.typesafe.scalalogging.StrictLogging
import io.prometheus.client.hotspot.DefaultExports

import org.alephium.explorer.error.FatalSystemExit
import org.alephium.explorer.util.FutureUtil._

object Main extends StrictLogging {

  def main(args: Array[String]): Unit = {
    logger.info("Starting Explorer")
    logger.info(s"Build info: $BuildInfo")

    DefaultExports.initialize()

    val system: ActorSystem           = ActorSystem()
    implicit val ec: ExecutionContext = system.dispatcher

    sideEffect {
      managed(system) { implicit system =>
        Explorer()
          .map { state =>
            //Successful start: Add shutdown hook.
            def awaitClose(): Unit =
              Await.result(state.close(), 30.seconds)

            sideEffect(scala.sys.addShutdownHook(awaitClose()))
            logger.info(s"Explorer boot-up successful in ${state.getClass.getSimpleName} mode!")
          }
          .recoverWith { err =>
            //Error start: Log the error.
            err match {
              case err: FatalSystemExit =>
                logger.error("FATAL SYSTEM ERROR!", err)

              case err =>
                logger.error("Explorer boot-up failed!", err)
            }
            Future.failed(err)
          }
      }
    }
  }
}
