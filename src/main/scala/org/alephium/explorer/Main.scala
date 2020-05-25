package org.alephium.explorer

import scala.concurrent.{Await, ExecutionContext}

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri
import com.typesafe.scalalogging.StrictLogging

import org.alephium.explorer.sideEffect
import org.alephium.util.Duration

object Main extends App with StrictLogging {

  logger.info("Starting Application")
  implicit val system: ActorSystem                = ActorSystem("Explorer")
  implicit val executionContext: ExecutionContext = system.dispatcher

  //TODO Introduce a configuration system
  val blockflowPort: Int = 10974
  val port: Int          = 9000

  val app: Application = new Application(port, Uri(s"http://localhost:$blockflowPort"))

  sideEffect(
    scala.sys.addShutdownHook(Await.result(app.stop, Duration.ofSecondsUnsafe(10).asScala)))
}
