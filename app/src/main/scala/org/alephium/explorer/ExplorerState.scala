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

import scala.collection.immutable.ArraySeq
import scala.concurrent.{ExecutionContext, Future}

import com.typesafe.scalalogging.StrictLogging
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import org.alephium.explorer.cache.{BlockCache, MetricCache, TransactionCache}
import org.alephium.explorer.config.{BootMode, ExplorerConfig}
import org.alephium.explorer.config.ExplorerConfig.Consensus
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
    new Database(config.bootMode)(executionContext, databaseConfig)

  implicit lazy val consensus: Consensus = config.consensus

  implicit lazy val blockCache: BlockCache =
    BlockCache(
      config.cacheRowCountReloadPeriod,
      config.cacheBlockTimesReloadPeriod,
      config.cacheLatestBlocksReloadPeriod
    )(groupSettings, executionContext, database.databaseConfig)

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

  lazy val marketService: MarketService = MarketService(config.market)

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

    override def subServices: ArraySeq[Service] =
      ArraySeq(
        httpServer,
        marketService,
        metricCache,
        transactionCache,
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
