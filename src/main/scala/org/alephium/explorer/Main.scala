package org.alephium.explorer

import scala.concurrent.{Await, ExecutionContext}

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.StrictLogging
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import org.alephium.explorer.sideEffect
import org.alephium.util.Duration

object Main extends App with StrictLogging {

  logger.info("Starting Application")
  implicit val system: ActorSystem                = ActorSystem("Explorer")
  implicit val executionContext: ExecutionContext = system.dispatcher

  val config: Config = ConfigFactory.load()

  val blockflowUri: Uri = {
    val host = config.getString("blockflow.host")
    val port = config.getInt("blockflow.port")
    Uri(s"http://$host:$port")

  }
  val port: Int = config.getInt("explorer.port")

  val databaseConfig: DatabaseConfig[JdbcProfile] = DatabaseConfig.forConfig[JdbcProfile]("db")

  val app: Application =
    new Application(port, blockflowUri, databaseConfig)

  sideEffect(
    scala.sys.addShutdownHook(Await.result(app.stop, Duration.ofSecondsUnsafe(10).asScala)))
}
