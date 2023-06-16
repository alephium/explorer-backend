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

package org.alephium.explorer.service

import scala.collection.immutable.ArraySeq
import scala.concurrent.{ExecutionContext, Future}

import com.typesafe.scalalogging.StrictLogging
import io.vertx.core.Vertx
import io.vertx.core.http.{HttpClient, WebSocket, HttpClientOptions}
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile
import sttp.model.Uri

import org.alephium.api
import org.alephium.explorer.GroupSetting
import org.alephium.explorer.cache.BlockCache
import org.alephium.explorer.error.ExplorerError.WebSocketError
import org.alephium.explorer.persistence.queries.InputUpdateQueries
import org.alephium.json.Json._
import org.alephium.util.discard

@SuppressWarnings(
  Array("org.wartremover.warts.NonUnitStatements",
        "org.wartremover.warts.Var",
        "org.wartremover.warts.OptionPartial",
        "org.wartremover.warts.IterableOps"))
case object WebSocketSyncService extends api.WebSocketEndpoints with StrictLogging {

  val maybeApiKey: Option[api.model.ApiKey] = None
  private var vertx: Vertx                  = _

  def start(blockFlowWsUri: Uri)(implicit ec: ExecutionContext,
                                 dc: DatabaseConfig[PostgresProfile],
                                 blockFlowClient: BlockFlowClient,
                                 cache: BlockCache,
                                 groupSetting: GroupSetting): Unit = {
    logger.debug("Starting web sockent syncing")
    vertx = Vertx.vertx()

    val client: HttpClient = {
      val options = new HttpClientOptions().setMaxWebSocketFrameSize(1024 * 1024)
      vertx.createHttpClient(options);
    }

    client.webSocket(
      blockFlowWsUri.port.get,
      blockFlowWsUri.host.get,
      blockWebSocket.showPathTemplate(), { asyncResult =>
        discard(
          asyncResult.map {
            ws: WebSocket =>
              ws.textMessageHandler { msg =>
                  val blockAndEvents =
                    BlockFlowClient.blockAndEventsToEntities(read[api.model.BlockAndEvents](msg))
                  discard(
                    for {
                      _ <- BlockFlowSyncService.insertBlocks(ArraySeq(blockAndEvents))
                      _ <- dc.db.run(InputUpdateQueries.updateInputs())
                    } yield {
                      ()
                    }
                  )
                }
                .closeHandler { _ =>
                  ec.reportFailure(WebSocketError("WebSocket closed, node must be down"))
                }
                .exceptionHandler { exception =>
                  ec.reportFailure(WebSocketError(s"WebSocket exception: $exception"))
                }
          }
        )
      }
    )
  }

  def stop(): Future[Unit] = {
    Future.successful(())
  }
}
