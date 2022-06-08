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
import scala.concurrent.duration._

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri
import com.typesafe.scalalogging.StrictLogging
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import org.alephium.api.model.{ApiKey, ChainParams, PeerAddress}
import org.alephium.explorer.cache.{BlockCache, TransactionCache}
import org.alephium.explorer.persistence.DBInitializer
import org.alephium.explorer.persistence.dao._
import org.alephium.explorer.service._
import org.alephium.explorer.util.FutureUtil._
import org.alephium.explorer.util.Scheduler
import org.alephium.protocol.model.NetworkId
import org.alephium.util.Duration

// scalastyle:off magic.number
class Application(host: String,
                  port: Int,
                  readOnly: Boolean,
                  blockFlowUri: Uri,
                  groupNum: Int,
                  networkId: NetworkId,
                  maybeBlockFlowApiKey: Option[ApiKey],
                  syncPeriod: Duration,
                  directCliqueAccess: Boolean,
                  bootTimout: FiniteDuration)(implicit system: ActorSystem,
                                              executionContext: ExecutionContext,
                                              databaseConfig: DatabaseConfig[PostgresProfile])
    extends StrictLogging {

  if (!readOnly) {
    DBInitializer.initialize().await(bootTimout)
  }

  implicit val scheduler: Scheduler =
    Scheduler(s"${classOf[Application].getSimpleName} scheduler")

  implicit val groupSetting: GroupSetting =
    GroupSetting(groupNum)

  implicit val blockCache: BlockCache =
    BlockCache()

  implicit val transactionCache: TransactionCache =
    TransactionCache().await(bootTimout)

  //Services
  implicit val blockFlowClient: BlockFlowClient =
    BlockFlowClient(blockFlowUri, groupNum, maybeBlockFlowApiKey)

  val server: AppServer =
    new AppServer(BlockService, TransactionService, TokenSupplyService)

  private val bindingPromise: Promise[Http.ServerBinding] = Promise()

  private def urisFromPeers(peers: Seq[PeerAddress]): Seq[Uri] = {
    peers.map { peer =>
      s"http://${peer.address.getHostAddress}:${peer.restPort}"
    }
  }

  private def getBlockFlowPeers(): Future[Seq[Uri]] = {
    if (directCliqueAccess) {
      blockFlowClient.fetchSelfClique().map {
        case Right(selfClique) =>
          val peers = urisFromPeers(selfClique.nodes.toSeq)
          logger.debug(s"Syncing with clique peers: $peers")
          peers
        case Left(error) =>
          logger.error(s"Cannot fetch self-clique: $error")
          sys.exit(1)
      }
    } else {
      logger.debug(s"Syncing with node: $blockFlowUri")
      Future.successful(Seq(blockFlowUri))
    }
  }

  @SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
  private def startSyncService(): Future[Unit] = {
    for {
      chainParams <- blockFlowClient.fetchChainParams()
      _           <- validateChainParams(chainParams)
      peers       <- getBlockFlowPeers()
      syncDuration = syncPeriod.millis.milliseconds
    } yield {
      BlockFlowSyncService.start(peers, syncDuration)
      MempoolSyncService.start(peers, syncDuration)
      TokenSupplyService.start(1.minute)
      HashrateService.start(1.minute)
      FinalizerService.start(10.minutes)
      TransactionHistoryService.start(15.minutes)
    }
  }

  private def startTasksForReadWriteApp(): Future[Unit] = {
    if (!readOnly) {
      startSyncService()
    } else {
      Future.successful(())
    }
  }

  def start: Future[Unit] = {
    for {
      _       <- HealthCheckDao.healthCheck()
      _       <- startTasksForReadWriteApp()
      binding <- Http().newServerAt(host, port).bindFlow(server.route)
    } yield {
      sideEffect(bindingPromise.success(binding))
      logger.info(s"Listening http request on $binding")
    }
  }

  def stop: Future[Unit] = {
    scheduler.close()
    for {
      _ <- bindingPromise.future.flatMap(_.unbind())
    } yield {
      logger.info("Application stopped")
    }
  }

  def validateChainParams(response: Either[String, ChainParams]): Future[Unit] = {
    response match {
      case Right(chainParams) =>
        if (chainParams.networkId =/= networkId) {
          logger.error(
            s"Chain id mismatch: ${chainParams.networkId} (remote) vs $networkId (local)")
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
