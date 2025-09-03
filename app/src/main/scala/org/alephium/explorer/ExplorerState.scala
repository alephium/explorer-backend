// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer

import scala.collection.immutable.ArraySeq
import scala.concurrent.{ExecutionContext, Future}

import com.typesafe.scalalogging.StrictLogging
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import org.alephium.explorer.cache._
import org.alephium.explorer.config.{BootMode, ExplorerConfig}
import org.alephium.explorer.persistence.Database
import org.alephium.explorer.service._
import org.alephium.explorer.util.Scheduler
import org.alephium.util.Service

/** Boot-up states for Explorer: Explorer can be started in the following three states
  *
  *   - ReadOnly: [[org.alephium.explorer.ExplorerState.ReadOnly]]
  *   - ReadWrite: [[org.alephium.explorer.ExplorerState.ReadWrite]]
  *   - WriteOnly: [[org.alephium.explorer.ExplorerState.WriteOnly]]
  */
sealed trait ExplorerState extends Service with StrictLogging {
  implicit def config: ExplorerConfig
  implicit def databaseConfig: DatabaseConfig[PostgresProfile]

  implicit lazy val groupSettings: GroupSetting =
    GroupSetting(config.groupNum)

  lazy val database: Database =
    new Database(config.bootMode)(executionContext, databaseConfig, config)

  implicit lazy val blockCache: BlockCache =
    BlockCache(
      config.cacheRowCountReloadPeriod,
      config.cacheBlockTimesReloadPeriod,
      config.cacheLatestBlocksReloadPeriod
    )(groupSettings, executionContext, database.databaseConfig)

  val addressTxCountCache: AddressTxCountCache

  implicit lazy val blockFlowClient: BlockFlowClient =
    BlockFlowClient(
      uri = config.blockFlowUri,
      groupNum = config.groupNum,
      maybeApiKey = config.maybeBlockFlowApiKey,
      directCliqueAccess = config.directCliqueAccess,
      consensus = config.consensus
    )

  override def startSelfOnce(): Future[Unit] = {
    Future.unit
  }

  override def stopSelfOnce(): Future[Unit] = {
    Future.unit
  }

}

sealed trait ExplorerStateRead extends ExplorerState {

  implicit lazy val metricCache: MetricCache =
    new MetricCache(
      database,
      config.cacheMetricsReloadPeriod
    )

  lazy val transactionCache: TransactionCache =
    TransactionCache(database)(executionContext)

  lazy val marketService: market.MarketService = market.MarketService(config.market)

  private lazy val routes =
    AppServer
      .routes(
        marketService,
        config.exportTxsNumberThreshold,
        config.streamParallelism,
        config.maxTimeInterval,
        config.market
      )(
        executionContext,
        database.databaseConfig,
        blockFlowClient,
        blockCache,
        metricCache,
        transactionCache,
        addressTxCountCache,
        groupSettings
      )
  lazy val httpServer: ExplorerHttpServer =
    new ExplorerHttpServer(
      config.host,
      config.port,
      routes,
      database
    )
}

sealed trait ExplorerStateWrite extends ExplorerState {

  val addressTxCountCache: AddressTxCountCache =
    DBAddressTxCountCache(database)

  // See issue #356
  implicit private val scheduler: Scheduler = Scheduler("SYNC_SERVICES")

  override def startSelfOnce(): Future[Unit] = {
    SyncServices.startSyncServices(config)
  }
}

object ExplorerState {

  def apply(mode: BootMode)(implicit
      config: ExplorerConfig,
      databaseConfig: DatabaseConfig[PostgresProfile],
      executionContext: ExecutionContext
  ): ExplorerState =
    mode match {
      case BootMode.ReadOnly  => ExplorerState.ReadOnly()
      case BootMode.ReadWrite => ExplorerState.ReadWrite()
      case BootMode.WriteOnly => ExplorerState.WriteOnly()
    }

  /** State of Explorer is started in read-only mode */
  final case class ReadOnly()(implicit
      val config: ExplorerConfig,
      val databaseConfig: DatabaseConfig[PostgresProfile],
      val executionContext: ExecutionContext
  ) extends ExplorerStateRead {

    val addressTxCountCache: AddressTxCountCache =
      InMemoryAddressTxCountCache(database)

    override def subServices: ArraySeq[Service] =
      ArraySeq(
        httpServer,
        marketService,
        metricCache,
        transactionCache,
        addressTxCountCache,
        database
      )
  }

  /** State of Explorer is started in read-write mode */
  final case class ReadWrite()(implicit
      val config: ExplorerConfig,
      val databaseConfig: DatabaseConfig[PostgresProfile],
      val executionContext: ExecutionContext
  ) extends ExplorerStateRead
      with ExplorerStateWrite {

    override def subServices: ArraySeq[Service] =
      ArraySeq(
        httpServer,
        marketService,
        metricCache,
        transactionCache,
        addressTxCountCache,
        database,
        blockFlowClient
      )
  }

  /** State of Explorer is started in Sync only mode */
  final case class WriteOnly()(implicit
      val config: ExplorerConfig,
      val databaseConfig: DatabaseConfig[PostgresProfile],
      val executionContext: ExecutionContext
  ) extends ExplorerStateWrite {

    override def subServices: ArraySeq[Service] =
      ArraySeq(
        blockFlowClient,
        database
      )
  }
}
