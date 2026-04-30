// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

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
import org.alephium.explorer.config.Default
import org.alephium.explorer.config.ExplorerConfig.Consensus
import org.alephium.explorer.persistence.model.BlockEntityWithEvents
import org.alephium.explorer.persistence.queries.InputUpdateQueries
import org.alephium.json.Json._
import org.alephium.protocol.config.GroupConfig
import org.alephium.rpc.model.JsonRPC.Notification
import org.alephium.util.{discard, Duration, TimeStamp}
import org.alephium.ws._
import org.alephium.ws.WsClient.KeepAlive
import org.alephium.ws.WsParams.WsNotificationParamsCodec
import org.alephium.ws.WsUtils._

@SuppressWarnings(
  Array(
    "org.wartremover.warts.NonUnitStatements",
    "org.wartremover.warts.Var",
    "org.wartremover.warts.DefaultArguments",
    "org.wartremover.warts.MutableDataStructures",
    "org.wartremover.warts.Recursion"
  )
)
case object WebSocketSyncService extends WsNotificationParamsCodec with StrictLogging {

  val maybeApiKey: Option[api.model.ApiKey] = None

  implicit val groupConfig: GroupConfig = Default.groupConfig
  private val batchAddress              = "blocks.batch"

  // Reconnection state
  @volatile private var reconnectAttempts: Int = 0
  @volatile private var blocksReceived: Long = 0
  @volatile private var blocksFlushed: Long = 0

// scalastyle:off parameter.number method.length
  def sync(
      stopPromise: Promise[Unit],
      host: String,
      port: Int,
      flushInterval: Duration,
      maxBlockDelay: Duration,
      maxBufferSize: Int,
      maxReconnectAttempts: Int,
      reconnectBaseDelay: Duration,
      reconnectMaxDelay: Duration
  )(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      blockFlowClient: BlockFlowClient,
      consensus: Consensus,
      cache: BlockCache,
      groupSetting: GroupSetting
  ): Unit = {

    def calculateBackoff(): Long = {
      val backoffMs = reconnectBaseDelay.millis * Math.pow(2, reconnectAttempts.toDouble).toLong
      Math.min(backoffMs, reconnectMaxDelay.millis)
    }

    def attemptConnection(): Unit = {
      val vertx: Vertx = Vertx.vertx()
      val wsClient = WsClient(vertx)

      def closeHandler(client: ClientWs, deploymentId: String): WebSocket = {
        client.underlying.closeHandler { _ =>
          logger.info(s"WebSocket connection closed (received: $blocksReceived, flushed: $blocksFlushed)")
          vertx.undeploy(deploymentId)

          // Attempt reconnection if we haven't exceeded max attempts
          if (reconnectAttempts < maxReconnectAttempts) {
            reconnectAttempts += 1
            val backoffMs = calculateBackoff()
            logger.warn(s"WebSocket connection lost. Reconnecting in ${backoffMs}ms (attempt $reconnectAttempts/$maxReconnectAttempts)")

            // Schedule reconnection
            vertx.setTimer(backoffMs, _ => {
              attemptConnection()
            })
          } else {
            logger.error(s"WebSocket failed after $maxReconnectAttempts attempts, falling back to HTTP syncing")
            reconnectAttempts = 0
            stopPromise.trySuccess(())
          }
          ()
        }
      }

      // format: off
      (for {
        client <- wsClient.connect(port, host)(notif => handleNotification(notif, vertx))(handleKeepAlive)
        blockBatching = new BlockBatchingVerticle(
          batchAddress,
          flushInterval,
          inserts,
          client,
          maxBlockDelay,
          maxBufferSize,
          () => blocksReceived += 1,
          () => blocksFlushed += 1
        )
        deploymentId <- vertx.deployVerticle(blockBatching).asScala
        _ = closeHandler(client, deploymentId)
        _ <- client.subscribeToBlock(0)
      } yield ())
        .onComplete {
          case Success(_) =>
            logger.info(s"WebSocket syncing started (host: $host, port: $port)")
            reconnectAttempts = 0 // Reset on successful connection
          case Failure(exception) =>
            reconnectAttempts += 1
            if (reconnectAttempts < maxReconnectAttempts) {
              val backoffMs = calculateBackoff()
              logger.warn(s"WebSocket connection failed. Retrying in ${backoffMs}ms (attempt $reconnectAttempts/$maxReconnectAttempts)", exception)
              vertx.setTimer(backoffMs, _ => {
                attemptConnection()
              })
            } else {
              logger.error(s"WebSocket syncing failed after $maxReconnectAttempts attempts, falling back to HTTP", exception)
              reconnectAttempts = 0
              stopPromise.trySuccess(())
            }
            ()
        }
      // format: on
    }

    attemptConnection()
  }
// scalastyle:on parameter.number

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
      client: ClientWs,
      maxBlockDelay: Duration,
      maxBufferSize: Int,
      onBlockReceived: () => Unit,
      onBlocksFlushed: () => Unit
  )(implicit
      ec: ExecutionContext,
      consensus: Consensus,
      groupSetting: GroupSetting
  ) extends AbstractVerticle {

    /*
     * Passing this delay, the websocket syncing will stop and go back to regular sync.
     * This is to prevent the case where the websocket become too slow for some reason.
     */
    private val delayAllowed = maxBlockDelay

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
        onBlockReceived()

        // Force flush if buffer exceeds max size
        // scalastyle:off magic.number
        if (buffer.lengthIs >= maxBufferSize) {
          logger.warn(s"Buffer size (${buffer.length}) exceeded max ($maxBufferSize), forcing flush")
          flush()
        }
        // scalastyle:on magic.number
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
        val batchSize = batch.size
        buffer = ArrayBuffer.empty
        pending = true

        logger.debug(s"Flushing ${batchSize} blocks to database")

        insertBlocksToDb(batch).onComplete { result =>
          pending = false

          result match {
            case Success(_) =>
              onBlocksFlushed()
              logger.debug(s"Successfully flushed ${batchSize} blocks")
            case Failure(ex) =>
              logger.error(s"DB insert failed: ${ex.getMessage}", ex)
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
