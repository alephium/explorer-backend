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

import org.alephium.api.model.{ApiKey, PeerAddress, SelfClique}
import org.alephium.explorer.persistence.DBInitializer
import org.alephium.explorer.persistence.dao._
import org.alephium.explorer.service._
import org.alephium.explorer.sideEffect
import org.alephium.protocol.model.NetworkId
import org.alephium.util.Duration

// scalastyle:off magic.number
class Application(
    host: String,
    port: Int,
    readOnly: Boolean,
    blockFlowUri: Uri,
    groupNum: Int,
    blockflowFetchMaxAge: Duration,
    networkId: NetworkId,
    databaseConfig: DatabaseConfig[JdbcProfile],
    maybeBlockFlowApiKey: Option[ApiKey],
    syncPeriod: Duration)(implicit system: ActorSystem, executionContext: ExecutionContext)
    extends StrictLogging {

  val dbInitializer: DBInitializer = DBInitializer(databaseConfig)

  val blockDao: BlockDao                = BlockDao(databaseConfig)
  val transactionDao: TransactionDao    = TransactionDao(databaseConfig)
  val utransactionDao: UnconfirmedTxDao = UnconfirmedTxDao(databaseConfig)
  val healthCheckDao: HealthCheckDao    = HealthCheckDao(databaseConfig)

  //Services
  val blockFlowClient: BlockFlowClient =
    BlockFlowClient.apply(blockFlowUri, groupNum, blockflowFetchMaxAge, maybeBlockFlowApiKey)

  val blockFlowSyncService: BlockFlowSyncService =
    BlockFlowSyncService(groupNum = groupNum, syncPeriod = syncPeriod, blockFlowClient, blockDao)

  val mempoolSyncService: MempoolSyncService =
    MempoolSyncService(syncPeriod = syncPeriod, blockFlowClient, utransactionDao)

  val tokenCirculationService: TokenCirculationService =
    TokenCirculationService(syncPeriod = Duration.ofMinutesUnsafe(1), databaseConfig, groupNum)

  val blockService: BlockService             = BlockService(blockDao)
  val transactionService: TransactionService = TransactionService(transactionDao, utransactionDao)

  val server: AppServer =
    new AppServer(blockService, transactionService, tokenCirculationService, blockflowFetchMaxAge)

  private val bindingPromise: Promise[Http.ServerBinding] = Promise()

  private def urisFromPeers(peers: Seq[PeerAddress]): Seq[Uri] = {
    peers.map { peer =>
      s"http://${peer.address.getHostAddress}:${peer.restPort}"
    }
  }

  @SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
  private def startSyncService(): Future[Unit] = {
    for {
      selfClique <- blockFlowClient.fetchSelfClique()
      _          <- validateSelfClique(selfClique)
      peers = urisFromPeers(selfClique.toOption.get.nodes.toSeq)
      _ <- blockFlowSyncService.start(peers)
      _ <- mempoolSyncService.start(peers)
      _ <- tokenCirculationService.start()
    } yield ()
  }

  def start: Future[Unit] = {
    for {
      _       <- healthCheckDao.healthCheck()
      _       <- dbInitializer.createTables()
      _       <- if (readOnly) Future.successful(()) else startSyncService()
      binding <- Http().newServerAt(host, port).bindFlow(server.route)
    } yield {
      sideEffect(bindingPromise.success(binding))
      logger.info(s"Listening http request on $binding")
    }
  }

  def stop: Future[Unit] =
    for {
      _ <- if (!readOnly) blockFlowSyncService.stop() else Future.successful(())
      _ <- bindingPromise.future.flatMap(_.unbind())
    } yield {
      logger.info("Application stopped")
    }

  def validateSelfClique(response: Either[String, SelfClique]): Future[Unit] = {
    response match {
      case Right(selfClique) =>
        if (selfClique.networkId =/= networkId) {
          logger.error(s"Chain id mismatch: ${selfClique.networkId} (remote) vs $networkId (local)")
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
