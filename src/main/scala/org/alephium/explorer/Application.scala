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

import org.alephium.api.model.Network
import org.alephium.explorer.persistence.dao.{BlockDao, TransactionDao}
import org.alephium.explorer.service._
import org.alephium.explorer.sideEffect
import org.alephium.protocol.model.NetworkType
import org.alephium.util.Duration

// scalastyle:off magic.number
class Application(host: String,
                  port: Int,
                  blockFlowUri: Uri,
                  groupNum: Int,
                  blockflowFetchMaxAge: Duration,
                  networkType: NetworkType,
                  databaseConfig: DatabaseConfig[JdbcProfile])(implicit system: ActorSystem,
                                                               executionContext: ExecutionContext)
    extends StrictLogging {

  val blockDao: BlockDao             = BlockDao(databaseConfig)
  val transactionDao: TransactionDao = TransactionDao(databaseConfig)

  //Services
  val blockFlowClient: BlockFlowClient =
    BlockFlowClient.apply(blockFlowUri, groupNum, networkType, blockflowFetchMaxAge)

  val blockFlowSyncService: BlockFlowSyncService =
    BlockFlowSyncService(groupNum   = groupNum,
                         syncPeriod = Duration.unsafe(15 * 1000),
                         blockFlowClient,
                         blockDao)
  val blockService: BlockService             = BlockService(blockDao)
  val transactionService: TransactionService = TransactionService(transactionDao)

  val server: AppServer = new AppServer(blockService, transactionService)

  private val bindingPromise: Promise[Http.ServerBinding] = Promise()

  sideEffect {
    for {
      network <- blockFlowClient.fetchNetwork()
      _       <- validateNetwork(network)
      _       <- blockFlowSyncService.start()
      binding <- Http().bindAndHandle(server.route, host, port)
    } yield {
      sideEffect(bindingPromise.success(binding))
      logger.info(s"Listening http request on $binding")
    }
  }

  def stop: Future[Unit] =
    for {
      _ <- blockFlowSyncService.stop()
      _ <- bindingPromise.future.flatMap(_.unbind())
    } yield {
      logger.info("Application stopped")
    }

  def validateNetwork(network: Either[String, Network]): Future[Unit] = {
    network match {
      case Right(network) =>
        if (network.networkType =/= networkType) {
          logger.error(
            s"Network type mismatch: ${network.networkType} (remote) vs $networkType (local)")
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
