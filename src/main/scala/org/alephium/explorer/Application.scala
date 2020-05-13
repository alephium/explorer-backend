package org.alephium.explorer

import scala.concurrent.{ExecutionContext, Future, Promise}

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import com.typesafe.scalalogging.StrictLogging

import org.alephium.explorer.api.model.BlockEntry
import org.alephium.explorer.service.{BlockService, Services}
import org.alephium.explorer.web.{BlockServer, Servers}
import org.alephium.util.{AVector, TimeStamp}

trait Application extends Services with Servers with StrictLogging {

  def port: Int

  implicit def system: ActorSystem
  implicit def executionContext: ExecutionContext

  val blockServiceImpl = new BlockService {
    def getBlockById(blockId: String): Future[Either[String, BlockEntry]] =
      Future.successful(Right(BlockEntry(blockId, TimeStamp.unsafe(0), 0, 0, 0, AVector.empty)))
  }

  override val blockService: BlockService = blockServiceImpl
  override val blockServer: BlockServer = new BlockServer {
    override def blockService: BlockService = blockServiceImpl
  }

  private val bindingPromise: Promise[Http.ServerBinding] = Promise()

  def start: Future[Unit] =
    for {
      binding <- Http().bindAndHandle(route, "localhost", port)
    } yield {
      bindingPromise.success(binding)
      logger.info(s"Listening http request on $binding")
    }

  def stop: Future[Unit] =
    bindingPromise.future
      .flatMap(_.unbind)
      .map {
        case _ => logger.info("Http Unbound.")
      }

}
