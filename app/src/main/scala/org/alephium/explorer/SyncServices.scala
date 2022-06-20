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

import akka.http.scaladsl.model.Uri
import com.typesafe.scalalogging.StrictLogging
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import org.alephium.api.model.{ChainParams, PeerAddress}
import org.alephium.explorer.cache.BlockCache
import org.alephium.explorer.config.ExplorerConfig
import org.alephium.explorer.error.ExplorerError._
import org.alephium.explorer.service._
import org.alephium.explorer.util.Scheduler
import org.alephium.protocol.model.NetworkId

/** Implements function for Sync Services boot-up sequence */
@SuppressWarnings(Array("org.wartremover.warts.Overloading"))
object SyncServices extends StrictLogging {

  def startSyncServices(config: ExplorerConfig)(implicit scheduler: Scheduler,
                                                ec: ExecutionContext,
                                                dc: DatabaseConfig[PostgresProfile],
                                                blockFlowClient: BlockFlowClient,
                                                blockCache: BlockCache,
                                                groupSetting: GroupSetting): Future[Unit] =
    if (config.readOnly) {
      Future.unit
    } else {
      getPeers(
        networkId          = config.networkId,
        directCliqueAccess = config.directCliqueAccess,
        blockFlowUri       = config.blockFlowUri
      ) flatMap { peers =>
        startSyncServices(
          peers                           = peers,
          syncPeriod                      = config.syncPeriod,
          tokenSupplyServiceSyncPeriod    = config.tokenSupplyServiceSyncPeriod,
          hashRateServiceSyncPeriod       = config.hashRateServiceSyncPeriod,
          finalizerServiceSyncPeriod      = config.finalizerServiceSyncPeriod,
          transactionHistoryServicePeriod = config.transactionHistoryServicePeriod
        )
      }
    }

  /** Start sync services given the peers */
  // scalastyle:off
  def startSyncServices(peers: Seq[Uri],
                        syncPeriod: FiniteDuration,
                        tokenSupplyServiceSyncPeriod: FiniteDuration,
                        hashRateServiceSyncPeriod: FiniteDuration,
                        finalizerServiceSyncPeriod: FiniteDuration,
                        transactionHistoryServicePeriod: FiniteDuration)(
      implicit scheduler: Scheduler,
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      blockFlowClient: BlockFlowClient,
      blockCache: BlockCache,
      groupSetting: GroupSetting): Future[Unit] = {
    Future.fromTry {
      Try {
        BlockFlowSyncService.start(peers, syncPeriod)
        MempoolSyncService.start(peers, syncPeriod)
        TokenSupplyService.start(tokenSupplyServiceSyncPeriod)
        HashrateService.start(hashRateServiceSyncPeriod)
        FinalizerService.start(finalizerServiceSyncPeriod)
        TransactionHistoryService.start(transactionHistoryServicePeriod)
      }
    }
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
