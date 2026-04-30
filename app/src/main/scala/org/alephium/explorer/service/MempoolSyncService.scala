// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.service

import scala.collection.immutable.ArraySeq
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.concurrent.duration.{Duration => ScalaDuration, FiniteDuration}
import scala.util._

import com.typesafe.scalalogging.StrictLogging
import io.vertx.core.AbstractVerticle
import io.vertx.core.Vertx
import io.vertx.core.eventbus.Message
import io.vertx.core.http.WebSocket
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile
import sttp.model.Uri

import org.alephium.api
import org.alephium.explorer.api.model.MempoolTransaction
import org.alephium.explorer.config.Default
import org.alephium.explorer.persistence.dao.MempoolDao
import org.alephium.explorer.util.Scheduler
import org.alephium.json.Json._
import org.alephium.protocol.config.GroupConfig
import org.alephium.rpc.model.JsonRPC.Notification
import org.alephium.util.{discard, Duration}
import org.alephium.ws._
import org.alephium.ws.WsClient.KeepAlive
import org.alephium.ws.WsParams.WsNotificationParamsCodec
import org.alephium.ws.WsUtils._

/*
 * Syncing mempool
 */

case object MempoolSyncService extends StrictLogging {

  def start(nodeUris: ArraySeq[Uri], interval: FiniteDuration)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      blockFlowClient: BlockFlowClient,
      scheduler: Scheduler
  ): Future[Unit] =
    scheduler.scheduleLoop(
      taskId = this.productPrefix,
      firstInterval = ScalaDuration.Zero,
      loopInterval = interval
    )(syncOnce(nodeUris))

  def syncOnce(nodeUris: ArraySeq[Uri])(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      blockFlowClient: BlockFlowClient
  ): Future[Unit] = {
    Future.sequence(nodeUris.map(syncMempool)).map(_ => ())
  }

  private def syncMempool(uri: Uri)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      blockFlowClient: BlockFlowClient
  ): Future[Unit] = {
    for {
      currentTxs <- blockFlowClient.fetchMempoolTransactions(uri)
      currentHashes = currentTxs.map(_.hash).toSet
      dbHashes <- MempoolDao.listHashes()
      toRemove = dbHashes.filterNot(currentHashes.contains)
      _ <- MempoolDao.removeAndInsertMany(toRemove, currentTxs)
    } yield ()
  }
}

@SuppressWarnings(
  Array(
    "org.wartremover.warts.NonUnitStatements",
    "org.wartremover.warts.Var",
    "org.wartremover.warts.DefaultArguments"
  )
)
case object MempoolWebSocketSyncService extends WsNotificationParamsCodec with StrictLogging {

  val maybeApiKey: Option[api.model.ApiKey] = None

  implicit val groupConfig: GroupConfig = Default.groupConfig
  private val batchAddress              = "blocks.batch"

  // scalastyle:off parameter.number
  def sync(stopPromise: Promise[Unit], host: String, port: Int, flushInterval: Duration)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
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
      txBatching = new TxBatchingVerticle(batchAddress, flushInterval, inserts, client)
      deploymentId <- vertx.deployVerticle(txBatching).asScala
      _ = closeHandler(client, deploymentId)
      _ <- client.subscribeToTx(0)
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

  def inserts(mempoolTxs: ArraySeq[MempoolTransaction])(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): Future[Unit] = {
    for {
      _ <- MempoolDao.insertMany(mempoolTxs)
    } yield (())
  }

  /*
   * This verticle is used to batch the blocks received from the websocket
   * and insert them into the database after every flushInterval.
   * Verticles are equivalent to akka actors
   */
  @SuppressWarnings(Array("org.wartremover.warts.MutableDataStructures"))
  private class TxBatchingVerticle(
      address: String,
      flushInterval: Duration,
      insertBlocksToDb: ArraySeq[MempoolTransaction] => Future[Unit],
      client: ClientWs
  )(implicit
      ec: ExecutionContext
  ) extends AbstractVerticle {

    /*
     * Vertx vertices are equivalent to akka actors
     * So message are process in a single thread and
     * with the security of `pending` we are safe
     * to use mutable data structure
     */
    private var buffer  = ArrayBuffer.empty[MempoolTransaction]
    private var pending = false

    override def start(): Unit = {
      discard(this.vertx.eventBus().consumer[Array[Byte]](address, handleBlock))
      discard(this.vertx.setPeriodic(flushInterval.millis, _ => flush()))
    }

    private def handleBlock(msg: Message[Array[Byte]]): Unit = {
      handleJsonMessage(readBinary[ujson.Value](msg.body())).foreach { mempoolTransaction =>
        buffer += mempoolTransaction
      }
    }

    private def handleJsonMessage(notification: ujson.Value): Option[MempoolTransaction] = {
      Try(read[WsParams.WsNotificationParams](notification)).toEither match {
        case Left(exception) =>
          logger.error(s"Failed to parse notification: $notification", exception)
          None
        case Right(params) =>
          params match {
            case WsParams.WsTxNotificationParams(_, transactionTemplate) =>
              val mempoolTransaction =
                BlockFlowClient.txTemplateToMempoolTx(transactionTemplate)

              // validateBlockAndEvents(mempoolTransaction)

              Some(mempoolTransaction)
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
  }
}
