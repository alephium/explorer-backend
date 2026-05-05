// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.service

import scala.collection.immutable.ArraySeq
import scala.concurrent.{ExecutionContext, Future}

import io.vertx.core.Vertx
import sttp.tapir.server.vertx.VertxFutureServerInterpreter._

import org.alephium.explorer.AlephiumFutureSpec
import org.alephium.explorer.ConfigDefaults._
import org.alephium.explorer.GenApiModel._
import org.alephium.explorer.GenDBModel._
import org.alephium.explorer.persistence.model.BlockEntityWithEvents
import org.alephium.json.Json._
import org.alephium.protocol.Hash
import org.alephium.util.{discard, Duration, TimeStamp}

@SuppressWarnings(
  Array(
    "org.wartremover.warts.Var",
    "org.wartremover.warts.MutableDataStructures",
    "org.wartremover.warts.DefaultArguments",
    "org.wartremover.warts.Null",
    "org.wartremover.warts.ThreadSleep"
  )
)
class BlockBatchingVerticleSpec extends AlephiumFutureSpec {

  implicit val ec: ExecutionContext = ExecutionContext.global

  def createTestBlock(timestamp: TimeStamp): BlockEntityWithEvents = {
    val chainIndex = chainIndexes.head
    val block      = blockEntityGen(chainIndex).sample.get
    BlockEntityWithEvents(block.copy(timestamp = timestamp), ArraySeq.empty)
  }

  def serializeBlockNotification(block: BlockEntityWithEvents): Array[Byte] = {
    val notification = ujson.Obj(
      "jsonrpc" -> "2.0",
      "method"  -> "subscription",
      "params" -> ujson.Obj(
        "subscription" -> block.block.hash.toHexString,
        "result" -> ujson.Obj(
          "type" -> "BlockNotification",
          "block" -> ujson.Obj(
            "hash"         -> block.block.hash.toHexString,
            "timestamp"    -> block.block.timestamp.millis,
            "chainFrom"    -> block.block.chainFrom.value,
            "chainTo"      -> block.block.chainTo.value,
            "height"       -> block.block.height.value,
            "deps"         -> ujson.Arr(),
            "transactions" -> ujson.Arr(),
            "nonce"        -> "",
            "version"      -> 0,
            "depStateHash" -> Hash.zero.toHexString,
            "txsHash"      -> Hash.zero.toHexString,
            "target"       -> "",
            "ghostUncles"  -> ujson.Arr()
          ),
          "events" -> ujson.Arr()
        )
      )
    )
    writeBinary(notification)
  }

  "BlockBatchingVerticle" should {
    "buffer and flush blocks periodically" in {
      val vertx   = Vertx.vertx()
      val address = "test.blocks.batch"
      var insertCount = 0
      val insertedBlocks = scala.collection.mutable.ArrayBuffer.empty[BlockEntityWithEvents]

      val insertFn: ArraySeq[BlockEntityWithEvents] => Future[Unit] = { blocks =>
        insertCount += 1
        insertedBlocks ++= blocks
        Future.successful(())
      }

      val flushInterval = Duration.ofMillisUnsafe(200L)
      val maxBlockDelay = Duration.ofSecondsUnsafe(30L)
      val maxBufferSize = 100

      val verticle = new WebSocketSyncService.BlockBatchingVerticle(
        address,
        flushInterval,
        insertFn,
        null,
        maxBlockDelay,
        maxBufferSize
      )

      try {
        val deploymentId = vertx.deployVerticle(verticle).asScala.futureValue

        val block1 = createTestBlock(TimeStamp.now())
        val block2 = createTestBlock(TimeStamp.now())

        vertx.eventBus().send(address, serializeBlockNotification(block1))
        vertx.eventBus().send(address, serializeBlockNotification(block2))

        Thread.sleep(300)

        insertCount is 1
        insertedBlocks.size is 2
        insertedBlocks.map(_.block.hash) should contain(block1.block.hash)
        insertedBlocks.map(_.block.hash) should contain(block2.block.hash)

        discard(vertx.undeploy(deploymentId).asScala.futureValue)
      } finally {
        discard(vertx.close().asScala.futureValue)
      }
    }

    "force flush when buffer exceeds max size" in {
      val vertx   = Vertx.vertx()
      val address = "test.blocks.batch.maxsize"
      var insertCount = 0
      val insertedBlocks = scala.collection.mutable.ArrayBuffer.empty[BlockEntityWithEvents]

      val insertFn: ArraySeq[BlockEntityWithEvents] => Future[Unit] = { blocks =>
        insertCount += 1
        insertedBlocks ++= blocks
        Future.successful(())
      }

      val flushInterval = Duration.ofSecondsUnsafe(10L)
      val maxBlockDelay = Duration.ofSecondsUnsafe(30L)
      val maxBufferSize = 3

      val verticle = new WebSocketSyncService.BlockBatchingVerticle(
        address,
        flushInterval,
        insertFn,
        null,
        maxBlockDelay,
        maxBufferSize
      )

      try {
        val deploymentId = vertx.deployVerticle(verticle).asScala.futureValue

        val blocks = (1 to 5).map(_ => createTestBlock(TimeStamp.now()))

        blocks.foreach { block =>
          vertx.eventBus().send(address, serializeBlockNotification(block))
          Thread.sleep(50)
        }

        eventually {
          insertCount should be > 0
          insertedBlocks.size should be >= 3
        }

        discard(vertx.undeploy(deploymentId).asScala.futureValue)
      } finally {
        discard(vertx.close().asScala.futureValue)
      }
    }

    "detect consistently old messages" in {
      val vertx   = Vertx.vertx()
      val address = "test.blocks.batch.old"

      val insertFn: ArraySeq[BlockEntityWithEvents] => Future[Unit] = { _ =>
        Future.successful(())
      }

      val flushInterval = Duration.ofSecondsUnsafe(10L)
      val maxBlockDelay = Duration.ofSecondsUnsafe(30L)
      val maxBufferSize = 100

      val verticle = new WebSocketSyncService.BlockBatchingVerticle(
        address,
        flushInterval,
        insertFn,
        null,
        maxBlockDelay,
        maxBufferSize
      )

      try {
        val deploymentId = vertx.deployVerticle(verticle).asScala.futureValue

        val oldTimestamp = TimeStamp.now().minusUnsafe(Duration.ofSecondsUnsafe(120L))
        val oldBlocks    = (1 to 6).map(_ => createTestBlock(oldTimestamp))

        oldBlocks.foreach { block =>
          vertx.eventBus().send(address, serializeBlockNotification(block))
          Thread.sleep(50)
        }

        eventually {
          verticle.shouldFallbackToHttp is true
        }

        discard(vertx.undeploy(deploymentId).asScala.futureValue)
      } finally {
        discard(vertx.close().asScala.futureValue)
      }
    }
  }
}
