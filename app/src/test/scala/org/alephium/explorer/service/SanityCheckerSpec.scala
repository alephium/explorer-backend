// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.service

import scala.collection.immutable.ArraySeq
import scala.concurrent.Future

import org.alephium.explorer.AlephiumFutureSpec
import org.alephium.explorer.GroupSetting
import org.alephium.explorer.GenApiModel.chainIndexes
import org.alephium.explorer.GenDBModel.blockEntityWithParentGen
import org.alephium.explorer.cache.TestBlockCache
import org.alephium.explorer.persistence.DatabaseFixtureForEach
import org.alephium.explorer.persistence.dao.BlockDao

class SanityCheckerSpec extends AlephiumFutureSpec with DatabaseFixtureForEach {

  "checkAndStopOnProblem" in {
    implicit val groupSetting: GroupSetting = GroupSetting(explorerConfig.groupNum)
    implicit val blockCache: org.alephium.explorer.cache.BlockCache = TestBlockCache()
    var fetchBlockCalls                     = 0

    implicit val blockFlowClient: BlockFlowClient = new EmptyBlockFlowClient {
      override def fetchBlock(
          fromGroup: org.alephium.protocol.model.GroupIndex,
          hash: org.alephium.protocol.model.BlockHash
      ): Future[org.alephium.explorer.persistence.model.BlockEntity] = {
        fetchBlockCalls += 1
        Future.failed(new AssertionError(s"unexpected fetch for $hash from $fromGroup"))
      }
    }

    val chainIndex = chainIndexes.head
    val parent     = blockEntityWithParentGen(chainIndex, None).sample.get.copy(mainChain = true)
    val child      = blockEntityWithParentGen(chainIndex, Some(parent)).sample.get.copy(mainChain = true)

    BlockDao.insertAll(ArraySeq(child)).futureValue

    val error = SanityChecker.checkAndStopOnProblem().failed.futureValue

    error.getMessage should include(child.hash.toHexString)
    error.getMessage should include(parent.hash.toHexString)
    fetchBlockCalls shouldBe 0
  }
}
