package org.alephium.explorer

import scala.concurrent.{ExecutionContext, Future, Promise}

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.OverflowStrategy
import com.typesafe.scalalogging.StrictLogging
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import org.alephium.explorer.persistence.dao.BlockDao
import org.alephium.explorer.persistence.db.DbBlockDao
import org.alephium.explorer.service.{BlockFlowClient, BlockFlowSyncService, BlockService}
import org.alephium.explorer.sideEffect
import org.alephium.explorer.web._
import org.alephium.util.Duration

// scalastyle:off magic.number
class Application(port: Int, blockFlowUri: Uri, databaseConfig: DatabaseConfig[JdbcProfile])(
    implicit system: ActorSystem,
    executionContext: ExecutionContext)
    extends StrictLogging
    with AkkaDecodeFailureHandler {

  val blockDao: BlockDao     = new DbBlockDao(databaseConfig)
  val httpClient: HttpClient = HttpClient(512, OverflowStrategy.fail)

  //Services
  val blockFlowClient: BlockFlowClient = BlockFlowClient.apply(httpClient, blockFlowUri)

  val blockFlowSyncService: BlockFlowSyncService =
    BlockFlowSyncService(groupNum   = 4,
                         syncPeriod = Duration.unsafe(15 * 1000),
                         blockFlowClient,
                         blockDao)
  val blockService: BlockService = BlockService(blockDao)

  //Servers
  val blockServer: BlockServer           = new BlockServer(blockService)
  val documentation: DocumentationServer = new DocumentationServer

  private val bindingPromise: Promise[Http.ServerBinding] = Promise()

  val route: Route = blockServer.route ~ documentation.route

  sideEffect {
    for {
      _       <- blockFlowSyncService.start()
      binding <- Http().bindAndHandle(route, "localhost", port)
    } yield {
      sideEffect(bindingPromise.success(binding))
      logger.info(s"Listening http request on $binding")
    }
  }

  def stop: Future[Unit] =
    for {
      _ <- blockFlowSyncService.stop()
      _ <- bindingPromise.future.flatMap(_.unbind)
    } yield {
      logger.info("Application stopped")
    }
}
