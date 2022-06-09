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

import com.typesafe.scalalogging.StrictLogging
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import org.alephium.explorer.cache.{BlockCache, TransactionCache}
import org.alephium.explorer.service.BlockFlowClient
import org.alephium.explorer.util.AsyncCloseable._
import org.alephium.explorer.util.Scheduler

/** Boot-up states for Explorer: Explorer can be started in the following two states
  *
  *  - ReadOnly: [[org.alephium.explorer.ExplorerState.ReadOnly]]
  *  - ReadWrite: [[org.alephium.explorer.ExplorerState.ReadWrite]]
  * */
sealed trait ExplorerState extends StrictLogging {
  def database: DatabaseConfig[PostgresProfile]
  def blockFlowClient: BlockFlowClient
  def groupSettings: GroupSetting
  def blockCache: BlockCache
  def transactionCache: TransactionCache
  def akkaHttp: AkkaHttpServer

  def schedulerOpt(): Option[Scheduler] =
    this match {
      case _: ExplorerState.ReadOnly =>
        None

      case state: ExplorerState.ReadWrite =>
        Some(state.scheduler)
    }

  def close()(implicit ec: ExecutionContext): Future[Unit] =
    schedulerOpt()
      .close() //Close scheduler first to stop all background processes eg: sync
      .recoverWith { throwable =>
        logger.error("Failed to close Scheduler", throwable)
        Future.unit
      }
      .flatMap { _ =>
        akkaHttp.stop() //Stop server to terminate receiving http requests
      }
      .flatMap { _ =>
        database
          .close() //close database
          .recoverWith { throwable =>
            logger.error("Failed to close database", throwable)
            Future.unit
          }
      }
}

object ExplorerState {

  def apply(scheduler: Option[Scheduler],
            database: DatabaseConfig[PostgresProfile],
            akkaHttpServer: AkkaHttpServer,
            blockFlowClient: BlockFlowClient,
            groupSettings: GroupSetting,
            blockCache: BlockCache,
            transactionCache: TransactionCache): ExplorerState =
    scheduler match {
      case Some(scheduler) =>
        ReadWrite(
          scheduler        = scheduler,
          database         = database,
          akkaHttp         = akkaHttpServer,
          blockFlowClient  = blockFlowClient,
          groupSettings    = groupSettings,
          blockCache       = blockCache,
          transactionCache = transactionCache
        )

      case None =>
        ReadOnly(
          database         = database,
          akkaHttp         = akkaHttpServer,
          blockFlowClient  = blockFlowClient,
          groupSettings    = groupSettings,
          blockCache       = blockCache,
          transactionCache = transactionCache
        )
    }

  /** State of Explorer is started in read-only mode */
  final case class ReadOnly(database: DatabaseConfig[PostgresProfile],
                            akkaHttp: AkkaHttpServer,
                            blockFlowClient: BlockFlowClient,
                            groupSettings: GroupSetting,
                            blockCache: BlockCache,
                            transactionCache: TransactionCache)
      extends ExplorerState

  /** State of Explorer is started in read-write mode */
  final case class ReadWrite(scheduler: Scheduler,
                             database: DatabaseConfig[PostgresProfile],
                             akkaHttp: AkkaHttpServer,
                             blockFlowClient: BlockFlowClient,
                             groupSettings: GroupSetting,
                             blockCache: BlockCache,
                             transactionCache: TransactionCache)
      extends ExplorerState
}
