// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.service

import java.math.BigInteger

import scala.collection.immutable.ArraySeq
import scala.concurrent.Future

import org.apache.pekko.util.ByteString

import org.alephium.explorer.AlephiumFutureSpec
import org.alephium.explorer.ConfigDefaults._
import org.alephium.explorer.api.model.Height
import org.alephium.explorer.cache.{BlockCache, TestBlockCache}
import org.alephium.explorer.persistence.DatabaseFixtureForEach
import org.alephium.explorer.persistence.TestDBRunner
import org.alephium.explorer.persistence.dao.BlockDao
import org.alephium.explorer.persistence.model.BlockEntity
import org.alephium.protocol.Hash
import org.alephium.protocol.model.BlockHash
import org.alephium.util.TimeStamp

class SanityCheckerSpec extends AlephiumFutureSpec with DatabaseFixtureForEach with TestDBRunner {

  private def runningFlag: AnyRef = {
    val field = SanityChecker.getClass.getDeclaredField("running")
    field.setAccessible(true)
    field.get(SanityChecker)
  }

  private def setRunning(value: Boolean): Unit = {
    val flag = runningFlag
    flag.getClass
      .getMethod("set", classOf[Boolean])
      .invoke(flag, java.lang.Boolean.valueOf(value))
    ()
  }

  "check" should {
    "download and insert a missing parent block" in {
      val chainIndex = groupSetting.chainIndexes.head
      val parentHash = BlockHash.unsafe(ByteString.fromArrayUnsafe(Array.fill[Byte](32)(1)))
      val childHash  = BlockHash.unsafe(ByteString.fromArrayUnsafe(Array.fill[Byte](32)(2)))
      val parent = BlockEntity(
        hash = parentHash,
        timestamp = TimeStamp.unsafe(0),
        chainFrom = chainIndex.from,
        chainTo = chainIndex.to,
        height = Height.unsafe(0),
        deps = ArraySeq.empty,
        transactions = ArraySeq.empty,
        inputs = ArraySeq.empty,
        outputs = ArraySeq.empty,
        mainChain = true,
        nonce = ByteString.empty,
        version = 1,
        depStateHash = Hash.unsafe(ByteString.fromArrayUnsafe(Array.fill[Byte](32)(3))),
        txsHash = Hash.unsafe(ByteString.fromArrayUnsafe(Array.fill[Byte](32)(4))),
        target = ByteString.empty,
        hashrate = BigInteger.ZERO,
        ghostUncles = ArraySeq.empty,
        conflictedTxs = None
      )
      val child = parent.copy(
        hash = childHash,
        timestamp = TimeStamp.unsafe(1),
        height = Height.unsafe(1),
        deps = ArraySeq.fill(groupSetting.groupNum)(parent.hash)
      )

      BlockDao.insert(child).futureValue

      implicit lazy val blockCache: BlockCache = TestBlockCache()
      implicit val blockFlowClient: BlockFlowClient = new EmptyBlockFlowClient {
        override def fetchBlock(
            fromGroup: org.alephium.protocol.model.GroupIndex,
            hash: org.alephium.protocol.model.BlockHash
        ): Future[org.alephium.explorer.persistence.model.BlockEntity] =
          Future.successful(parent.copy(mainChain = true))
      }

      SanityChecker
        .check()(
          executionContext,
          databaseConfig,
          blockFlowClient,
          blockCache,
          groupSetting
        )
        .futureValue

      assert(BlockDao.get(child.hash).futureValue.exists(_.mainChain))
      assert(BlockDao.get(parent.hash).futureValue.exists(_.mainChain))
    }

    "return immediately when another run is active" in {
      setRunning(true)
      try {
        SanityChecker
          .check()(
            executionContext,
            databaseConfig,
            new EmptyBlockFlowClient {},
            TestBlockCache(),
            groupSetting
          )
          .futureValue
      } finally {
        setRunning(false)
      }
    }
  }
}
