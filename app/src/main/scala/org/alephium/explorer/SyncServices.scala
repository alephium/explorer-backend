// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer

import java.time.LocalTime

import scala.collection.immutable.ArraySeq
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

import com.typesafe.scalalogging.StrictLogging
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile
import sttp.model.Uri

import org.alephium.api.model.{ChainParams, PeerAddress}
import org.alephium.explorer.RichAVector._
import org.alephium.explorer.cache.BlockCache
import org.alephium.explorer.config.{BootMode, ExplorerConfig}
import org.alephium.explorer.error.ExplorerError._
import org.alephium.explorer.service._
import org.alephium.explorer.util.Scheduler
import org.alephium.protocol.model.NetworkId
import org.alephium.util

/** Implements function for Sync Services boot-up sequence */
@SuppressWarnings(Array("org.wartremover.warts.Overloading"))
object SyncServices extends StrictLogging {

  def startSyncServices(config: ExplorerConfig)(implicit
      scheduler: Scheduler,
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      blockFlowClient: BlockFlowClient,
      blockCache: BlockCache,
      groupSetting: GroupSetting
  ): Future[Unit] =
    config.bootMode match {
      case BootMode.ReadOnly =>
        Future.unit

      case BootMode.ReadWrite | BootMode.WriteOnly =>
        logger.info("Starting sync services")
        getPeers(
          networkId = config.networkId,
          directCliqueAccess = config.directCliqueAccess,
          blockFlowUri = config.blockFlowUri
        ) flatMap { peers =>
          startSyncServices(
            peers = peers,
            syncPeriod = config.syncPeriod,
            holderServiceScheduleTime = config.holderServiceScheduleTime,
            activeAddressHistoryServiceScheduleTime =
              config.activeAddressHistoryServiceScheduleTime,
            tokenSupplyServiceScheduleTime = config.tokenSupplyServiceScheduleTime,
            hashRateServiceSyncPeriod = config.hashRateServiceSyncPeriod,
            finalizerServiceSyncPeriod = config.finalizerServiceSyncPeriod,
            transactionHistoryServiceSyncPeriod = config.transactionHistoryServiceSyncPeriod,
            blockFlowFetchMaxAge = config.blockFlowFetchMaxAge
          )
        }
    }

  /** Start sync services given the peers */
  // scalastyle:off
  def startSyncServices(
      peers: ArraySeq[Uri],
      syncPeriod: FiniteDuration,
      holderServiceScheduleTime: LocalTime,
      activeAddressHistoryServiceScheduleTime: LocalTime,
      tokenSupplyServiceScheduleTime: LocalTime,
      hashRateServiceSyncPeriod: FiniteDuration,
      finalizerServiceSyncPeriod: FiniteDuration,
      transactionHistoryServiceSyncPeriod: FiniteDuration,
      blockFlowFetchMaxAge: FiniteDuration
  )(implicit
      scheduler: Scheduler,
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      blockFlowClient: BlockFlowClient,
      blockCache: BlockCache,
      groupSetting: GroupSetting
  ): Future[Unit] =
    Future.fromTry {
      Try {
        Future
          .sequence(
            ArraySeq(
              BlockFlowSyncService
                .start(peers, syncPeriod, util.Duration.unsafe(blockFlowFetchMaxAge.toMillis)),
              MempoolSyncService.start(peers, syncPeriod),
              TokenSupplyService.start(tokenSupplyServiceScheduleTime),
              HashrateService.start(hashRateServiceSyncPeriod),
              FinalizerService.start(finalizerServiceSyncPeriod),
              HolderService.start(holderServiceScheduleTime),
              ActiveAddressHistoryService.start(activeAddressHistoryServiceScheduleTime),
              TransactionHistoryService.start(transactionHistoryServiceSyncPeriod)
            )
          )
          .onComplete {
            case Failure(error) =>
              val failure = error match {
                case psql: org.postgresql.util.PSQLException =>
                  DatabaseError(psql)
                case other => other
              }
              logger.error(s"Fatal error while syncing: $error")
              ec.reportFailure(failure)

            case Success(_) => ()
          }
      }
    }

  /** Fetch network peers */
  def getPeers(networkId: NetworkId, directCliqueAccess: Boolean, blockFlowUri: Uri)(implicit
      ec: ExecutionContext,
      blockFlowClient: BlockFlowClient
  ): Future[ArraySeq[Uri]] =
    blockFlowClient
      .fetchChainParams()
      .flatMap { chainParams =>
        val validationResult =
          validateChainParams(
            networkId = networkId,
            chainParams = chainParams
          )

        validationResult match {
          case Failure(exception) =>
            Future.failed(exception)

          case Success(_) =>
            getBlockFlowPeers(
              directCliqueAccess = directCliqueAccess,
              blockFlowUri = blockFlowUri
            )
        }
      }

  /** Converts `PeerAddress` to `Uri` */
  def urisFromPeers(peers: ArraySeq[PeerAddress]): ArraySeq[Uri] =
    peers.map { peer =>
      Uri(peer.address.getHostAddress, peer.restPort)
    }

  def getBlockFlowPeers(directCliqueAccess: Boolean, blockFlowUri: Uri)(implicit
      ec: ExecutionContext,
      blockFlowClient: BlockFlowClient
  ): Future[ArraySeq[Uri]] =
    if (directCliqueAccess) {
      blockFlowClient.fetchSelfClique() flatMap { selfClique =>
        if (selfClique.nodes.isEmpty) {
          Future.failed(PeersNotFound(blockFlowUri))
        } else {

          val peers = urisFromPeers(selfClique.nodes.toArraySeq)
          logger.debug(s"Syncing with clique peers: $peers")
          Future.successful(peers)
        }
      }
    } else {
      logger.debug(s"Syncing with node: $blockFlowUri")
      Future.successful(ArraySeq(blockFlowUri))
    }

  def validateChainParams(networkId: NetworkId, chainParams: ChainParams): Try[Unit] =
    if (chainParams.networkId =/= networkId) {
      Failure(ChainIdMismatch(chainParams.networkId, networkId))
    } else {
      Success(())
    }
}
