package org.alephium.explorer

import scala.concurrent.{Await, ExecutionContext}

import akka.actor.ActorSystem
import com.typesafe.scalalogging.StrictLogging

import org.alephium.explorer.sideEffect
import org.alephium.util.Duration

object Main extends App with StrictLogging {

  logger.info("Starting Application")
  val app: Application = new Application {
    val port: Int                                   = 9000
    implicit val system: ActorSystem                = ActorSystem("Explorer")
    implicit val executionContext: ExecutionContext = system.dispatcher
  }

  sideEffect(app.start)

  sideEffect(
    scala.sys.addShutdownHook(Await.result(app.stop, Duration.ofSecondsUnsafe(10).asScala)))
}
