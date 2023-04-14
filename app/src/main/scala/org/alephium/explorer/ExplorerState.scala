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

import org.alephium.explorer.cache.{BlockCache, TransactionCache}
import org.alephium.explorer.config.ExplorerConfig
import org.alephium.explorer.persistence.Database
import org.alephium.explorer.service._
import org.alephium.explorer.util.Scheduler
import org.alephium.util.Service

/** Boot-up states for Explorer: Explorer can be started in the following two states
  *
  *  - ReadOnly: [[org.alephium.explorer.ExplorerState.ReadOnly]]
  *  - ReadWrite: [[org.alephium.explorer.ExplorerState.ReadWrite]]
  * */
sealed trait ExplorerState extends Service with StrictLogging {
  implicit def config: ExplorerConfig
  implicit def databaseConfig: DatabaseConfig[PostgresProfile]

  implicit lazy val groupSettings: GroupSetting =
    GroupSetting(config.groupNum)

  lazy val database: Database =
    new Database(config.bootMode)(executionContext, databaseConfig)

  implicit lazy val blockCache: BlockCache =
    BlockCache(config.cacheRowCountReloadPeriod,
               config.cacheBlockTimesReloadPeriod,
               config.cacheLatestBlocksReloadPeriod)(groupSettings,
                                                     executionContext,
                                                     database.databaseConfig)

  lazy val transactionCache: TransactionCache =
    TransactionCache(database)(executionContext)

  implicit lazy val blockFlowClient: BlockFlowClient =
    BlockFlowClient(
      uri                = config.blockFlowUri,
      groupNum           = config.groupNum,
      maybeApiKey        = config.maybeBlockFlowApiKey,
      directCliqueAccess = config.directCliqueAccess
    )

  override def startSelfOnce(): Future[Unit] = {
    Future.unit
  }

  override def stopSelfOnce(): Future[Unit] = {
    Future.unit
  }

  def customServices: ArraySeq[Service]

  override def subServices: ArraySeq[Service] = {
    val writeOnlyServices =
      ArraySeq(
        transactionCache,
        blockFlowClient,
        database
      )

    customServices ++ writeOnlyServices
  }

}

sealed trait ExplorerStateRead extends ExplorerState {
  lazy val httpServer: ExplorerHttpServer =
    new ExplorerHttpServer(
      config.host,
      config.port,
      AppServer.routes(config.exportTxsNumberThreshold, config.streamParallelism)(
        executionContext,
        database.databaseConfig,
        blockFlowClient,
        blockCache,
        transactionCache,
        groupSettings)
    )

  override lazy val customServices: ArraySeq[Service] = ArraySeq(httpServer)
}

object ExplorerState {

  /** State of Explorer is started in read-only mode */
  final case class ReadOnly()(implicit val config: ExplorerConfig,
                              val databaseConfig: DatabaseConfig[PostgresProfile],
                              val executionContext: ExecutionContext)
      extends ExplorerStateRead

  /** State of Explorer is started in read-write mode */
  final case class ReadWrite()(implicit val config: ExplorerConfig,
                               val databaseConfig: DatabaseConfig[PostgresProfile],
                               val executionContext: ExecutionContext)
      extends ExplorerStateRead {

    private implicit val scheduler = Scheduler("SYNC_SERVICES")

    override def startSelfOnce(): Future[Unit] = {
      SyncServices.startSyncServices(config)
    }
  }

  /** State of Explorer is started in Sync only mode */
  final case class WriteOnly()(implicit val config: ExplorerConfig,
                               val databaseConfig: DatabaseConfig[PostgresProfile],
                               val executionContext: ExecutionContext)
      extends ExplorerState {

    //See issue #356
    private implicit val scheduler = Scheduler("SYNC_SERVICES")

    override lazy val customServices: ArraySeq[Service] = ArraySeq()

    override def startSelfOnce(): Future[Unit] = {
      SyncServices.startSyncServices(config)
    }
  }
}
