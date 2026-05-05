// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.service

import scala.collection.immutable.ArraySeq
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util._

import com.typesafe.scalalogging.StrictLogging
import io.vertx.core.AbstractVerticle
import io.vertx.core.Vertx
import io.vertx.core.eventbus.Message
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
  private val batchAddress = "blocks.batch"

  final case class ReconnectState(attempts: Int = 0)

  final case class StaleBlockTracker(
      consecutiveStaleBlocks: Int = 0,
      firstStaleBlockAt: Option[TimeStamp] = None
  )

  sealed trait StaleBlockDecision
  object StaleBlockDecision {
    case object Continue extends StaleBlockDecision
    case object CloseAndFallbackToHttp extends StaleBlockDecision
  }

  def calculateBackoff(attempt: Int, baseDelay: Duration, maxDelay: Duration): Long = {
    val multiplier = Math.pow(2, attempt.toDouble).toLong
    Math.min(baseDelay.millis * multiplier, maxDelay.millis)
  }

  def nextReconnect(
      state: ReconnectState,
      maxAttempts: Int,
      baseDelay: Duration,
      maxDelay: Duration
  ): Either[Unit, (ReconnectState, Long)] = {
    val nextState = state.copy(attempts = state.attempts + 1)

    if (nextState.attempts < maxAttempts) {
      Right(nextState -> calculateBackoff(nextState.attempts, baseDelay, maxDelay))
    } else {
      Left(())
    }
  }

  def checkBlockFreshness(
      state: StaleBlockTracker,
      blockTimestamp: TimeStamp,
      now: TimeStamp,
      delayAllowed: Duration,
      maxConsecutiveStaleBlocks: Int,
      staleBlockWindowDuration: Duration
  ): (StaleBlockTracker, StaleBlockDecision) = {

    val isStale = (now -- blockTimestamp).map(_ > delayAllowed).getOrElse(true)

    if (!isStale) {
      StaleBlockTracker() -> StaleBlockDecision.Continue
    } else {
      val nextState =
        state.copy(
          consecutiveStaleBlocks = state.consecutiveStaleBlocks + 1,
          firstStaleBlockAt = state.firstStaleBlockAt.orElse(Some(now))
        )

      val timeSinceFirstStale = nextState.firstStaleBlockAt.flatMap(now -- _)

      val shouldClose =
        nextState.consecutiveStaleBlocks >= maxConsecutiveStaleBlocks ||
          timeSinceFirstStale.exists(_ >= staleBlockWindowDuration)

      val decision: StaleBlockDecision =
        if (shouldClose) {
          StaleBlockDecision.CloseAndFallbackToHttp
        } else {
          StaleBlockDecision.Continue
        }

      (nextState, decision)
    }
  }

  def parseBlockNotification(
      notification: ujson.Value
  )(implicit consensus: Consensus, groupSetting: GroupSetting): Either[Throwable, BlockEntityWithEvents] =
    Try(read[WsParams.WsNotificationParams](notification)).toEither.flatMap {
      case WsParams.WsBlockNotificationParams(_, protocolBlockAndEvents) =>
        Right(BlockFlowClient.blockAndEventsToEntities(protocolBlockAndEvents))

      case other =>
        Left(new RuntimeException(s"Expected block notification, got: $other"))
    }

  def handleNotification(notification: Notification, vertx: Vertx): Unit = {
    notification.method match {
      case "subscription" =>
        discard(vertx.eventBus().send(batchAddress, writeBinary(notification.params)))
      case _ =>
        ()
    }
  }

  def handleKeepAlive(keepAlive: KeepAlive): Unit =
    logger.debug(s"Keep alive: $keepAlive")

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
    } yield ()

// scalastyle:off parameter.number method.length
  def sync(
      vertx: Vertx,
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

    var reconnectState = ReconnectState()

    def fallbackToHttp(reason: String): Unit = {
      logger.warn(reason)
      reconnectState = ReconnectState()
      stopPromise.trySuccess(())
      ()
    }

    def scheduleReconnect(): Unit = {
      nextReconnect(
        reconnectState,
        maxReconnectAttempts,
        reconnectBaseDelay,
        reconnectMaxDelay
      ) match {
        case Right((nextState, delayMs)) =>
          reconnectState = nextState
          logger.warn(
            s"WebSocket connection lost. Reconnecting in ${delayMs}ms " +
              s"(attempt ${nextState.attempts}/$maxReconnectAttempts)"
          )
          discard(vertx.setTimer(delayMs, _ => attemptConnection()))

        case Left(_) =>
          fallbackToHttp(
            s"WebSocket failed after $maxReconnectAttempts attempts, falling back to HTTP syncing"
          )
      }
    }

    def attemptConnection(): Unit = {
      val wsClient = WsClient(vertx)

      val connection =
        for {
          client <- wsClient.connect(port, host)(notif => handleNotification(notif, vertx))(handleKeepAlive)

          blockBatching = new BlockBatchingVerticle(
            address = batchAddress,
            flushInterval = flushInterval,
            insertBlocksToDb = inserts,
            client = client,
            maxBlockDelay = maxBlockDelay,
            maxBufferSize = maxBufferSize
          )

          deploymentId <- vertx.deployVerticle(blockBatching).asScala

          _ = client.underlying.closeHandler { _ =>
            logger.info("WebSocket connection closed")
            discard(vertx.undeploy(deploymentId))

            if (blockBatching.shouldFallbackToHttp) {
              fallbackToHttp("Falling back to HTTP syncing due to consistently old messages")
            } else {
              scheduleReconnect()
            }
          }

          _ <- client.subscribeToBlock(0)
        } yield ()

      connection.onComplete {
        case Success(_) =>
          reconnectState = ReconnectState()
          logger.info(s"WebSocket syncing started (host: $host, port: $port)")

        case Failure(exception) =>
          logger.warn("WebSocket connection failed", exception)
          scheduleReconnect()
      }
    }

    attemptConnection()
  }
// scalastyle:on parameter.number

  /*
   * This verticle is used to batch the blocks received from the websocket
   * and insert them into the database after every flushInterval.
   * Verticles are equivalent to akka actors
   */
  @SuppressWarnings(Array("org.wartremover.warts.MutableDataStructures"))
  private[service] class BlockBatchingVerticle(
      address: String,
      flushInterval: Duration,
      insertBlocksToDb: ArraySeq[BlockEntityWithEvents] => Future[Unit],
      client: ClientWs,
      maxBlockDelay: Duration,
      maxBufferSize: Int
  )(implicit
      ec: ExecutionContext,
      consensus: Consensus,
      groupSetting: GroupSetting
  ) extends AbstractVerticle {

    private val maxConsecutiveStaleBlocks = 5
    private val staleBlockWindowDuration  = Duration.ofSecondsUnsafe(10)

    private var buffer = Vector.empty[BlockEntityWithEvents]
    private var pending = false
    private var closing = false
    private var staleBlockTracker = StaleBlockTracker()
    @volatile private var shouldFallbackToHttpFlag: Boolean = false

    def shouldFallbackToHttp: Boolean = shouldFallbackToHttpFlag

    override def start(): Unit = {
      discard(this.vertx.eventBus().consumer[Array[Byte]](address, handleBlock))
      discard(this.vertx.setPeriodic(flushInterval.millis, _ => flush()))
    }

    private def handleBlock(msg: Message[Array[Byte]]): Unit = {
      if (!closing) {
        val parsed =
          parseBlockNotification(readBinary[ujson.Value](msg.body()))

        parsed match {
          case Left(error) =>
            logger.error(s"Failed to parse notification: ${msg.body().mkString}", error)

          case Right(blockAndEvents) =>
            validate(blockAndEvents)

            if (!closing) {
              buffer = buffer :+ blockAndEvents

              if (buffer.sizeIs >= maxBufferSize) {
                logger.warn(
                  s"Buffer size (${buffer.size}) exceeded max ($maxBufferSize), forcing flush"
                )
                flush()
              }
            }
        }
      }
    }

    private def validate(blockAndEvents: BlockEntityWithEvents): Unit = {
      val previous = staleBlockTracker

      val (nextState, decision) =
        checkBlockFreshness(
          state = previous,
          blockTimestamp = blockAndEvents.block.timestamp,
          now = TimeStamp.now(),
          delayAllowed = maxBlockDelay,
          maxConsecutiveStaleBlocks = maxConsecutiveStaleBlocks,
          staleBlockWindowDuration = staleBlockWindowDuration
        )

      staleBlockTracker = nextState

      decision match {
        case StaleBlockDecision.Continue =>
          if (previous.consecutiveStaleBlocks > 0 && nextState.consecutiveStaleBlocks == 0) {
            logger.debug(
              s"Received fresh block, resetting stale block counter (was: ${previous.consecutiveStaleBlocks})"
            )
          } else if (nextState.consecutiveStaleBlocks > 0) {
            logger.debug(
              s"Received stale block (${nextState.consecutiveStaleBlocks}/$maxConsecutiveStaleBlocks), monitoring..."
            )
          }

        case StaleBlockDecision.CloseAndFallbackToHttp =>
          closing = true
          shouldFallbackToHttpFlag = true

          logger.error(
            s"WebSocket blocks consistently stale " +
              s"(consecutive: ${nextState.consecutiveStaleBlocks}) - falling back to HTTP sync"
          )

          discard(client.close())
      }
    }

    private def flush(): Unit = {
      if (buffer.nonEmpty && !pending) {
        val batch = ArraySeq.from(buffer)
        buffer = Vector.empty
        pending = true

        logger.debug(s"Flushing ${batch.size} blocks to database")

        insertBlocksToDb(batch).onComplete {
          case Success(_) =>
            pending = false
            logger.debug(s"Successfully flushed ${batch.size} blocks")

          case Failure(ex) =>
            pending = false
            closing = true
            logger.error(s"DB insert failed: ${ex.getMessage}", ex)
            discard(client.close())
        }
      }
    }
  }
}
