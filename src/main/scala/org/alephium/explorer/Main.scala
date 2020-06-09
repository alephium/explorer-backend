package org.alephium.explorer

import scala.concurrent.{Await, ExecutionContext}

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri
import com.typesafe.scalalogging.StrictLogging
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import org.alephium.explorer.sideEffect
import org.alephium.util.Duration

object Main extends App with StrictLogging {

  logger.info("Starting Application")
  implicit val system: ActorSystem                = ActorSystem("Explorer")
  implicit val executionContext: ExecutionContext = system.dispatcher

  //TODO Introduce a configuration system
  val blockflowPort: Int = 10973
  val port: Int          = 9000

  val databaseConfig: DatabaseConfig[JdbcProfile] = DatabaseConfig.forConfig[JdbcProfile]("db")

  val app: Application =
    new Application(port, Uri(s"http://localhost:$blockflowPort"), databaseConfig)

  sideEffect(
    scala.sys.addShutdownHook(Await.result(app.stop, Duration.ofSecondsUnsafe(10).asScala)))
}
