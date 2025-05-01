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
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util._

import com.typesafe.scalalogging.StrictLogging
import io.vertx.core.AbstractVerticle
import io.vertx.core.Vertx
import io.vertx.core.eventbus.Message
import io.vertx.core.http.WebSocket
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import org.alephium.api
import org.alephium.explorer.GroupSetting
import org.alephium.explorer.cache.BlockCache
import org.alephium.explorer.config.ExplorerConfig.Consensus
import org.alephium.explorer.persistence.model.BlockEntityWithEvents
import org.alephium.explorer.persistence.queries.InputUpdateQueries
import org.alephium.json.Json._
import org.alephium.rpc.model.JsonRPC.Notification
import org.alephium.util.{discard, Duration, TimeStamp}
import org.alephium.ws._
import org.alephium.ws.WsClient.KeepAlive
import org.alephium.ws.WsParams.WsNotificationParams._
import org.alephium.ws.WsUtils._

@SuppressWarnings(
  Array(
    "org.wartremover.warts.NonUnitStatements",
    "org.wartremover.warts.Var",
    "org.wartremover.warts.DefaultArguments"
  )
)
case object WebSocketSyncService extends StrictLogging {

  val maybeApiKey: Option[api.model.ApiKey] = None

  private val batchAddress = "blocks.batch"

// scalastyle:off parameter.number
  def sync(stopPromise: Promise[Unit], host: String, port: Int, flushInterval: Duration)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      blockFlowClient: BlockFlowClient,
      consensus: Consensus,
      cache: BlockCache,
      groupSetting: GroupSetting
  ): Unit = {

    val vertx: Vertx = Vertx.vertx()

    val wsClient = WsClient(vertx)

    def closeHandler(client: ClientWs, deploymentId: String): WebSocket = {
      client.underlying.closeHandler { _ =>
        logger.info("WebSocket connection closed")
        vertx.undeploy(deploymentId)
        stopPromise.trySuccess(())
        ()
      }
    }

    // format: off
    (for {
      client <- wsClient.connect(port, host)(notif => handleNotification(notif, vertx))(handleKeepAlive)
      blockBatching = new BlockBatchingVerticle(batchAddress, flushInterval, inserts, client)
      deploymentId <- vertx.deployVerticle(blockBatching).asScala
      _ = closeHandler(client, deploymentId)
      _ <- client.subscribeToBlock(0)
    } yield ())
      .onComplete {
        case Success(_) =>
          logger.info("WebSocket syncing started")
        case Failure(exception) =>
          logger.error("WebSocket syncing failed", exception)
          stopPromise.trySuccess(())
          ()
      }
    // format: on
  }

  def handleNotification(notification: Notification, vertx: Vertx): Unit = {
    notification.method match {
      case "subscription" =>
        discard(vertx.eventBus().send(batchAddress, writeBinary(notification.params)))
      case _ =>
        // TODO Should we support error notification?
        ()
    }
  }

  def handleKeepAlive(keepAlive: KeepAlive): Unit = {
    // TODO: do we want to do something with this?
    //       I think ping are anyway automatcially handled by the vertx client
    logger.debug(s"Keep alive: ${keepAlive}")
  }

  def inserts(blockAndEvents: ArraySeq[BlockEntityWithEvents])(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      blockFlowClient: BlockFlowClient,
      cache: BlockCache,
      groupSetting: GroupSetting
  ): Future[Unit] =
    for {
      _ <- BlockFlowSyncService.insertBlocks(blockAndEvents)
      _ <- dc.db.run(InputUpdateQueries.updateInputs())
    } yield (())

  /*
   * This verticle is used to batch the blocks received from the websocket
   * and insert them into the database after every flushInterval.
   * Verticles are equivalent to akka actors
   */
  @SuppressWarnings(Array("org.wartremover.warts.MutableDataStructures"))
  private class BlockBatchingVerticle(
      address: String,
      flushInterval: Duration,
      insertBlocksToDb: ArraySeq[BlockEntityWithEvents] => Future[Unit],
      client: ClientWs
  )(implicit
      ec: ExecutionContext,
      consensus: Consensus,
      groupSetting: GroupSetting
  ) extends AbstractVerticle {

    /*
     * Passing this delay, the websocket syincg will stop and go back to regular sync.
     * This is to prevent the case where the websocket become too slow for some reason.
     */

    // TODO: this should be configurable
    // scalastyle:off magic.number
    private val delayAllowed = Duration.ofSecondsUnsafe(30L)
    // scalastyle:on magic.number

    /*
     * Vertx vertices are equivalent to akka actors
     * So message are process in a single thread and
     * with the security of `pending` we are safe
     * to use mutable data structure
     */
    private var buffer  = ArrayBuffer.empty[BlockEntityWithEvents]
    private var pending = false

    override def start(): Unit = {
      discard(this.vertx.eventBus().consumer[Array[Byte]](address, handleBlock))
      discard(this.vertx.setPeriodic(flushInterval.millis, _ => flush()))
    }

    private def handleBlock(msg: Message[Array[Byte]]): Unit = {
      handleJsonMessage(readBinary[ujson.Value](msg.body())).foreach { blockAndEvents =>
        buffer += blockAndEvents
      }
    }

    private def handleJsonMessage(notification: ujson.Value): Option[BlockEntityWithEvents] = {
      Try(read[WsParams.WsNotificationParams](notification)).toEither match {
        case Left(exception) =>
          logger.error(s"Failed to parse notification: $notification", exception)
          None
        case Right(params) =>
          params match {
            case WsParams.WsBlockNotificationParams(_, protocolBlockAndEvents) =>
              val blockAndEvents =
                BlockFlowClient.blockAndEventsToEntities(protocolBlockAndEvents)

              validateBlockAndEvents(blockAndEvents)

              Some(blockAndEvents)
            case other =>
              logger.error(s"Expected block notification, got: $other")
              None
          }
      }
    }

    private def flush(): Unit = {
      if (buffer.nonEmpty && !pending) {
        val batch = ArraySeq.from(buffer)
        buffer = ArrayBuffer.empty
        pending = true

        insertBlocksToDb(batch).onComplete { result =>
          pending = false

          result.failed.foreach { ex =>
            logger.error(s"DB insert failed: ${ex.getMessage}")
            // Resume to normal sync
            client.close()
          }
        }
      }
    }

    private def validateBlockAndEvents(
        blockAndEvents: BlockEntityWithEvents
    ): Unit = {
      if (
        (TimeStamp.now() -- blockAndEvents.block.timestamp)
          .map(_ > delayAllowed)
          .getOrElse(true)
      ) {
        logger.error("WebSocket message is to old")
        discard(client.close())
      }
    }
  }
}
