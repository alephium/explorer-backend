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
import org.alephium.explorer.service.{BlockFlowSyncService, BlockService}
import org.alephium.explorer.sideEffect
import org.alephium.explorer.web.{BlockServer, DocumentationServer, HttpClient}

class Application(port: Int, blockFlowUri: Uri)(implicit system: ActorSystem,
                                                executionContext: ExecutionContext)
    extends StrictLogging {

  val databaseConfig: DatabaseConfig[JdbcProfile] = DatabaseConfig.forConfig[JdbcProfile]("db")
  val blockDao: BlockDao                          = new DbBlockDao(databaseConfig)
  val httpClient: HttpClient                      = HttpClient(32, OverflowStrategy.dropNew)

  //Services
  val blockFlowSyncService: BlockFlowSyncService =
    BlockFlowSyncService(httpClient, blockFlowUri, blockDao)
  val blockService: BlockService = BlockService(blockDao)

  sideEffect(blockFlowSyncService.sync())

  //Servers
  val blockServer: BlockServer           = new BlockServer(blockService)
  val documentation: DocumentationServer = new DocumentationServer

  private val bindingPromise: Promise[Http.ServerBinding] = Promise()

  val route: Route = blockServer.route ~ documentation.route

  sideEffect {
    for {
      binding <- Http().bindAndHandle(route, "localhost", port)
    } yield {
      sideEffect(bindingPromise.success(binding))
      logger.info(s"Listening http request on $binding")
    }
  }

  def stop: Future[Unit] =
    bindingPromise.future
      .flatMap(_.unbind)
      .map {
        case _ => logger.info("Http Unbound.")
      }

}
