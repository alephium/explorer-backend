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

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.StrictLogging
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import org.alephium.api.model.{ChainParams, PeerAddress}
import org.alephium.explorer.cache.{BlockCache, TransactionCache}
import org.alephium.explorer.config.{ApplicationConfig, ExplorerConfig}
import org.alephium.explorer.error.ExplorerError._
import org.alephium.explorer.persistence.DBInitializer
import org.alephium.explorer.persistence.dao.HealthCheckDao
import org.alephium.explorer.service._
import org.alephium.explorer.util.AsyncCloseable._
import org.alephium.explorer.util.FutureUtil._
import org.alephium.explorer.util.Scheduler
import org.alephium.protocol.model.NetworkId

/** Implements function for Explorer boot-up sequence */
@SuppressWarnings(Array("org.wartremover.warts.Overloading"))
object Explorer extends StrictLogging {

  /** Start Explorer via `application.conf` */
  def start()(implicit ec: ExecutionContext, system: ActorSystem): Future[ExplorerState] =
    Try(ConfigFactory.load()) match {
      case Failure(exception) => Future.failed(exception)
      case Success(config)    => Explorer.start(config)
    }

  /** Start Explorer from parsed/loaded `application.conf` */
  def start(config: Config)(implicit ec: ExecutionContext,
                            system: ActorSystem): Future[ExplorerState] =
    ApplicationConfig(config) match {
      case Failure(exception)         => Future.failed(exception)
      case Success(applicationConfig) => Explorer.start(applicationConfig)
    }

  /** Start Explorer from typed [[org.alephium.explorer.config.ApplicationConfig]] */
  def start(applicationConfig: ApplicationConfig)(implicit ec: ExecutionContext,
                                                  system: ActorSystem): Future[ExplorerState] =
    ExplorerConfig(applicationConfig) match {
      case Failure(exception)      => Future.failed(exception)
      case Success(explorerConfig) => Explorer.start(explorerConfig)
    }

  /**
    * Start Explorer from validated [[org.alephium.explorer.config.ExplorerConfig]].
    *
    * This function implements the sequence for starting Explorer.
    * */
  // scalastyle:off
  def start(config: ExplorerConfig)(implicit ec: ExecutionContext,
                                    system: ActorSystem): Future[ExplorerState] =
    //First: Check database is available
    managed(initialiseDatabase(config.readOnly, "db", ConfigFactory.load())) { implicit dc =>
      implicit val blockFlowClient: BlockFlowClient =
        BlockFlowClient(
          uri         = config.blockFlowUri,
          groupNum    = config.groupNum,
          maybeApiKey = config.maybeBlockFlowApiKey
        )

      //Second: Fetch Peers and initialise Scheduler if configured for read-write mode.
      getPeersAndScheduler(config) flatMap { peersAndScheduler =>
        //managed termination of the scheduler during further initialisation.
        managed(peersAndScheduler.map(_._2)) { scheduler =>
          TransactionCache() flatMap { implicit transactionCache =>
            implicit val groupSetting: GroupSetting =
              GroupSetting(config.groupNum)

            implicit val blockCache: BlockCache =
              BlockCache()

            def startServer() =
              startHttpServer(
                host = config.host,
                port = config.port
              )

            //Third: Start the server.
            managed(startServer()) { akkaHttpServer =>
              //Create explorer's state: Either ReadOnly or ReadWrite
              implicit val state: ExplorerState =
                ExplorerState(
                  scheduler        = scheduler,
                  database         = dc,
                  akkaHttpServer   = akkaHttpServer,
                  blockFlowClient  = blockFlowClient,
                  groupSettings    = groupSetting,
                  blockCache       = blockCache,
                  transactionCache = transactionCache
                )

              peersAndScheduler match {
                case Some((peers, scheduler)) => //Is read-write mode
                  implicit val schedulerImplicit: Scheduler = scheduler

                  //Fourth/Finally: Start sync services.
                  startSyncServices(
                    peers                           = peers,
                    syncPeriod                      = config.syncPeriod,
                    tokenSupplyServiceSyncPeriod    = config.tokenSupplyServiceSyncPeriod,
                    hashRateServiceSyncPeriod       = config.hashRateServiceSyncPeriod,
                    finalizerServiceSyncPeriod      = config.finalizerServiceSyncPeriod,
                    transactionHistoryServicePeriod = config.transactionHistoryServicePeriod
                  )

                  Future.successful(state)

                case None => //Is read-only mode
                  Future.successful(state)
              }
            }
          }
        }
      }
    }
  // scalastyle:on

  /** If `readOnly = true` fetches peers and initialises [[org.alephium.explorer.util.Scheduler]] else returns None */
  def getPeersAndScheduler(config: ExplorerConfig)(
      implicit ec: ExecutionContext,
      blockFlowClient: BlockFlowClient): Future[Option[(Seq[Uri], Scheduler)]] =
    getPeers(config) map {
      case Some(peers) => Some((peers, Scheduler("SYNC_SERVICES")))
      case None        => None
    }

  /** Start sync services given the peers */
  // scalastyle:off
  def startSyncServices(peers: Seq[Uri],
                        syncPeriod: FiniteDuration,
                        tokenSupplyServiceSyncPeriod: FiniteDuration,
                        hashRateServiceSyncPeriod: FiniteDuration,
                        finalizerServiceSyncPeriod: FiniteDuration,
                        transactionHistoryServicePeriod: FiniteDuration)(
      implicit ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      blockFlowClient: BlockFlowClient,
      blockCache: BlockCache,
      groupSetting: GroupSetting,
      scheduler: Scheduler,
      closer: ExplorerCloser): Unit = {
    BlockFlowSyncService.start(peers, syncPeriod)
    MempoolSyncService.start(peers, syncPeriod)
    TokenSupplyService.start(tokenSupplyServiceSyncPeriod)
    HashrateService.start(hashRateServiceSyncPeriod)
    FinalizerService.start(finalizerServiceSyncPeriod)
    TransactionHistoryService.start(transactionHistoryServicePeriod)
  }

  /** Start AkkaHttp server */
  def startHttpServer(host: String, port: Int)(
      implicit system: ActorSystem,
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      blockFlowClient: BlockFlowClient,
      blockCache: BlockCache,
      transactionCache: TransactionCache,
      groupSetting: GroupSetting): Future[AkkaHttpServer] = {
    val routes =
      AppServer.routes()

    val httpServer =
      Http()
        .newServerAt(host, port)
        .bindFlow(routes)

    //routes are required by test-cases.
    httpServer map { server =>
      AkkaHttpServer(
        server      = server,
        routes      = routes,
        actorSystem = system
      )
    }
  }
  // scalastyle:on

  def getPeers(config: ExplorerConfig)(implicit ec: ExecutionContext,
                                       blockFlowClient: BlockFlowClient): Future[Option[Seq[Uri]]] =
    if (config.readOnly) {
      Future.successful(None)
    } else {
      getPeers(
        networkId          = config.networkId,
        directCliqueAccess = config.directCliqueAccess,
        blockFlowUri       = config.blockFlowUri
      ).map(Some(_))
    }

  /** Fetch network peers */
  def getPeers(networkId: NetworkId, directCliqueAccess: Boolean, blockFlowUri: Uri)(
      implicit ec: ExecutionContext,
      blockFlowClient: BlockFlowClient): Future[Seq[Uri]] =
    blockFlowClient
      .fetchChainParams()
      .flatMap { chainParams =>
        val validationResult =
          validateChainParams(
            networkId = networkId,
            response  = chainParams
          )

        validationResult match {
          case Failure(exception) =>
            Future.failed(exception)

          case Success(_) =>
            getBlockFlowPeers(
              directCliqueAccess = directCliqueAccess,
              blockFlowUri       = blockFlowUri
            )
        }
      }

  /**
    * Initialise the database from the config file.
    *
    * TODO: Make config a part of `ExplorerConfig`
    *
    * @param readOnly If true tried to validate connection else initialises schema.
    * @param path     Path of database config in `application.conf` file.
    * @param ec       Application Level execution (Not used for DB calls)
    *
    * @return Slick config provides Ability to run queries.
    */
  def initialiseDatabase(readOnly: Boolean, path: String, config: Config)(
      implicit ec: ExecutionContext): Future[DatabaseConfig[PostgresProfile]] =
    managed(DatabaseConfig.forConfig[PostgresProfile](path, config)) { implicit dc =>
      if (readOnly) {
        HealthCheckDao.healthCheck().mapSyncToVal(dc)
      } else {
        DBInitializer.initialize().mapSyncToVal(dc)
      }
    }

  /** Converts `PeerAddress` to `Uri` */
  def urisFromPeers(peers: Seq[PeerAddress]): Seq[Uri] =
    peers.map { peer =>
      s"http://${peer.address.getHostAddress}:${peer.restPort}"
    }

  def getBlockFlowPeers(directCliqueAccess: Boolean, blockFlowUri: Uri)(
      implicit ec: ExecutionContext,
      blockFlowClient: BlockFlowClient): Future[Seq[Uri]] =
    if (directCliqueAccess) {
      blockFlowClient.fetchSelfClique() flatMap {
        case Right(selfClique) if selfClique.nodes.isEmpty =>
          Future.failed(PeersNotFound(blockFlowUri))

        case Right(selfClique) =>
          val peers = urisFromPeers(selfClique.nodes.toSeq)
          logger.debug(s"Syncing with clique peers: $peers")
          Future.successful(peers)

        case Left(error) =>
          Future.failed(FailedToFetchSelfClique(error))
      }
    } else {
      logger.debug(s"Syncing with node: $blockFlowUri")
      Future.successful(Seq(blockFlowUri))
    }

  def validateChainParams(networkId: NetworkId, response: Either[String, ChainParams]): Try[Unit] =
    response match {
      case Right(chainParams) =>
        if (chainParams.networkId =/= networkId) {
          Failure(ChainIdMismatch(chainParams.networkId, networkId))
        } else {
          Success(())
        }

      case Left(err) =>
        Failure(ImpossibleToFetchNetworkType(err))
    }
}
