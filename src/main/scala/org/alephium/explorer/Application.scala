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

import scala.concurrent.{ExecutionContext, Future, Promise}

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri
import com.typesafe.scalalogging.StrictLogging
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import org.alephium.api.model.{PeerAddress, SelfClique}
import org.alephium.explorer.persistence.dao.{BlockDao, HealthCheckDao, TransactionDao}
import org.alephium.explorer.service._
import org.alephium.explorer.sideEffect
import org.alephium.protocol.model.NetworkType
import org.alephium.util.Duration

// scalastyle:off magic.number
class Application(host: String,
                  port: Int,
                  blockFlowUri: Uri,
                  groupNum: Int,
                  blockflowFetchMaxAge: Duration,
                  networkType: NetworkType,
                  databaseConfig: DatabaseConfig[JdbcProfile])(implicit system: ActorSystem,
                                                               executionContext: ExecutionContext)
    extends StrictLogging {

  val blockDao: BlockDao             = BlockDao(databaseConfig)
  val transactionDao: TransactionDao = TransactionDao(databaseConfig)
  val healthCheckDao: HealthCheckDao = HealthCheckDao(databaseConfig)

  //Services
  val blockFlowClient: BlockFlowClient =
    BlockFlowClient.apply(blockFlowUri, groupNum, networkType, blockflowFetchMaxAge)

  val blockFlowSyncService: BlockFlowSyncService =
    BlockFlowSyncService(groupNum   = groupNum,
                         syncPeriod = Duration.unsafe(15 * 1000),
                         blockFlowClient,
                         blockDao)
  val blockService: BlockService             = BlockService(blockDao)
  val transactionService: TransactionService = TransactionService(transactionDao)

  val server: AppServer =
    new AppServer(blockService, transactionService, networkType, blockflowFetchMaxAge)

  private val bindingPromise: Promise[Http.ServerBinding] = Promise()

  private def urisFromPeers(peers: Seq[PeerAddress]): Seq[Uri] = {
    peers.map { peer =>
      s"http://${peer.address.getHostAddress}:${peer.restPort}"
    }
  }

  @SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
  def start: Future[Unit] = {
    for {
      _          <- healthCheckDao.healthCheck()
      _          <- blockDao.createTables()
      selfClique <- blockFlowClient.fetchSelfClique()
      _          <- validateSelfClique(selfClique)
      _          <- blockFlowSyncService.start(urisFromPeers(selfClique.toOption.get.nodes.toSeq))
      binding    <- Http().newServerAt(host, port).bindFlow(server.route)
    } yield {
      sideEffect(bindingPromise.success(binding))
      logger.info(s"Listening http request on $binding")
    }
  }

  def stop: Future[Unit] =
    for {
      _ <- blockFlowSyncService.stop()
      _ <- bindingPromise.future.flatMap(_.unbind())
    } yield {
      logger.info("Application stopped")
    }

  def validateSelfClique(response: Either[String, SelfClique]): Future[Unit] = {
    response match {
      case Right(selfClique) =>
        if (selfClique.networkType =/= networkType) {
          logger.error(
            s"Network type mismatch: ${selfClique.networkType} (remote) vs $networkType (local)")
          sys.exit(1)
        } else {
          Future.successful(())
        }
      case Left(err) =>
        logger.error(s"Impossible to fetch network type: $err")
        sys.exit(1)
    }
  }
}
