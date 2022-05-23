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

import scala.concurrent.{Await, ExecutionContext, Future, Promise}
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
                  syncPeriod: Duration)(implicit system: ActorSystem,
                                        executionContext: ExecutionContext,
                                        databaseConfig: DatabaseConfig[PostgresProfile])
    extends StrictLogging {

  //TOTO: Temporary placeholder for executing services returning a Future.
  //      This will eventually be removed and the all services will get started
  //      asynchronously via the start function.
  def awaitService[T](f: => Future[T]): T =
    Await.result(f, 5.seconds)

  implicit val scheduler: Scheduler =
    Scheduler(s"${classOf[Application].getSimpleName} scheduler")

  implicit val groupSetting: GroupSetting =
    GroupSetting(groupNum)

  implicit val blockCache: BlockCache =
    BlockCache()

  implicit val transactionCache: TransactionCache =
    awaitService(TransactionCache())

  //Services
  val blockFlowClient: BlockFlowClient =
    BlockFlowClient(blockFlowUri, groupNum, maybeBlockFlowApiKey)

  val blockFlowSyncService: BlockFlowSyncService =
    BlockFlowSyncService(groupNum = groupNum, syncPeriod = syncPeriod, blockFlowClient)

  val mempoolSyncService: MempoolSyncService =
    MempoolSyncService(syncPeriod = syncPeriod, blockFlowClient, UnconfirmedTxDao)

  val sanityChecker: SanityChecker =
    new SanityChecker(groupNum, blockFlowClient)

  val server: AppServer =
    new AppServer(BlockService, TransactionService, TokenSupplyService, sanityChecker)

  private val bindingPromise: Promise[Http.ServerBinding] = Promise()

  private def urisFromPeers(peers: Seq[PeerAddress]): Seq[Uri] = {
    peers.map { peer =>
      s"http://${peer.address.getHostAddress}:${peer.restPort}"
    }
  }

  @SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
  private def startSyncService(): Future[Unit] = {
    for {
      selfClique  <- blockFlowClient.fetchSelfClique()
      chainParams <- blockFlowClient.fetchChainParams()
      _           <- validateChainParams(chainParams)
      peers = urisFromPeers(selfClique.toOption.get.nodes.toSeq)
      _ <- blockFlowSyncService.start(peers)
      _ <- mempoolSyncService.start(peers)
      _ <- TokenSupplyService.start(1.minute)
      _ <- HashrateService.start(1.minute)
      _ <- FinalizerService.start(10.minutes)
    } yield ()
  }

  private def startTasksForReadWriteApp(): Future[Unit] = {
    if (!readOnly) {
      for {
        _ <- DBInitializer.initialize()
        _ <- startSyncService()
      } yield ()

    } else {
      Future.successful(())
    }
  }

  private def stopTasksForReadWriteApp(): Future[Unit] = {
    if (!readOnly) {
      blockFlowSyncService.stop()
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
      _ <- stopTasksForReadWriteApp()
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
