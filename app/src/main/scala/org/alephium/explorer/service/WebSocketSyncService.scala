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

import scala.annotation.nowarn
import scala.collection.immutable.ArraySeq
import scala.concurrent.{ExecutionContext, Future, Promise}

import com.typesafe.scalalogging.StrictLogging
import io.vertx.core.{AsyncResult, Handler, Vertx}
import io.vertx.core.http.{HttpClient, HttpClientOptions, WebSocket}
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile
import sttp.model.Uri

import org.alephium.api
import org.alephium.explorer.GroupSetting
import org.alephium.explorer.cache.BlockCache
import org.alephium.explorer.error.ExplorerError.WebSocketError
import org.alephium.explorer.persistence.model.BlockEntityWithEvents
import org.alephium.explorer.persistence.queries.InputUpdateQueries
import org.alephium.json.Json._
import org.alephium.util.{Duration, TimeStamp}

@SuppressWarnings(
  Array(
    "org.wartremover.warts.NonUnitStatements",
    "org.wartremover.warts.Var",
    "org.wartremover.warts.OptionPartial",
    "org.wartremover.warts.IterableOps"
  )
)
case object WebSocketSyncService extends api.WebSocketEndpoints with StrictLogging {

  val maybeApiKey: Option[api.model.ApiKey] = None
  private val vertx: Vertx                  = Vertx.vertx()
  private val client: HttpClient = {
    val options = new HttpClientOptions().setMaxWebSocketFrameSize(1024 * 1024)
    vertx.createHttpClient(options);
  }

  // scalastyle:off magic.number
  private val delayAllowed = Duration.ofSecondsUnsafe(30L)
  // scalastyle:on magic.number

  def sync(blockFlowWsUri: Uri, stopPromise: Promise[Unit])(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      blockFlowClient: BlockFlowClient,
      cache: BlockCache,
      groupSetting: GroupSetting
  ): Unit = {
    logger.debug("Starting web socket syncing")
    client.webSocket(
      blockFlowWsUri.port.get,
      blockFlowWsUri.host.get,
      blockWebSocket.showPathTemplate(),
      webSocketHandler(stopPromise)
    )
  }

  @nowarn
  def webSocketHandler(stopPromise: Promise[Unit])(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      blockFlowClient: BlockFlowClient,
      cache: BlockCache,
      groupSetting: GroupSetting
  ): Handler[AsyncResult[WebSocket]] = { asyncResult =>
    asyncResult.map { ws: WebSocket =>
      ws.textMessageHandler { msg =>
        handleMessage(msg).map {
          case Left(cause) =>
            logger.debug(cause)
            stopPromise.trySuccess(())
            ws.close()
          case _ => ()
        }
      }.closeHandler { _ =>
        stopPromise.trySuccess(())
      }.exceptionHandler { exception =>
        ec.reportFailure(WebSocketError(exception))
      }
    }
  }

  def handleMessage(message: String)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      blockFlowClient: BlockFlowClient,
      cache: BlockCache,
      groupSetting: GroupSetting
  ): Future[Either[String, Unit]] = {
    val blockAndEvents =
      BlockFlowClient.blockAndEventsToEntities(read[api.model.BlockAndEvents](message))

    validateBlockAndEvents(blockAndEvents) match {
      case Left(cause) => Future.successful(Left(cause))
      case Right(_)    => insert(blockAndEvents).map(Right(_))
    }
  }

  def insert(blockAndEvents: BlockEntityWithEvents)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      blockFlowClient: BlockFlowClient,
      cache: BlockCache,
      groupSetting: GroupSetting
  ): Future[Unit] =
    for {
      _ <- BlockFlowSyncService.insertBlocks(ArraySeq(blockAndEvents))
      _ <- dc.db.run(InputUpdateQueries.updateInputs())
    } yield ()

  def validateBlockAndEvents(blockAndEvents: BlockEntityWithEvents): Either[String, Unit] =
    for {
      _ <- validateTimestamp(blockAndEvents.block.timestamp)
    } yield ()

  def validateTimestamp(timestamp: TimeStamp): Either[String, Unit] = {
    if (
      (TimeStamp.now() -- timestamp)
        .map(_ > delayAllowed)
        .getOrElse(true)
    ) {
      Left("WebSocket message is to old")
    } else {
      Right(())
    }
  }
}
