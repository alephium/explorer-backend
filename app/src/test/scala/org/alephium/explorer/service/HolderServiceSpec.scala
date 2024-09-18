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

import java.math.BigInteger

import scala.collection.immutable.ArraySeq

import slick.jdbc.PostgresProfile.api._

import org.alephium.api.model
import org.alephium.explorer.AlephiumActorSpecLike
import org.alephium.explorer.ConfigDefaults._
import org.alephium.explorer.GenApiModel._
import org.alephium.explorer.GenCoreApi._
import org.alephium.explorer.GenCoreProtocol._
import org.alephium.explorer.api.model._
import org.alephium.explorer.cache.{BlockCache, TestBlockCache}
import org.alephium.explorer.persistence._
import org.alephium.explorer.persistence.dao._
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.persistence.schema.CustomGetResult._
import org.alephium.explorer.util.SlickUtil._
import org.alephium.protocol.model.{Address, ChainIndex, GroupIndex}
import org.alephium.util.{Duration, TimeStamp, U256}

class HolderServiceSpec extends AlephiumActorSpecLike with DatabaseFixtureForEach {

  "insert initial table" in new Fixture {
    val blocks = chainGen(4, TimeStamp.now(), ChainIndex.unsafe(0, 0)).sample.get
    val blockEntities: Seq[BlockEntity] = blocks.map(BlockFlowClient.blockProtocolToEntity)

    insertAndFinalize(blockEntities)
    HolderService.sync().futureValue

    checkBalances(blocks)
  }

  "update table" in new Fixture {
    val blocks = chainGen(4, TimeStamp.now(), ChainIndex.unsafe(0, 0)).sample.get
    val blockEntities: Seq[BlockEntity] = blocks.map(BlockFlowClient.blockProtocolToEntity)

    val firstBlocksSize = blockEntities.size / 2
    val firstBlocks     = blockEntities.take(firstBlocksSize)
    insertAndFinalize(firstBlocks)
    HolderService.sync().futureValue

    checkBalances(blocks.take(firstBlocksSize))
    // Update table with the rest of the blocks

    val newBlocks = blockEntities.drop(firstBlocks.length)
    insertAndFinalize(newBlocks)
    HolderService.sync().futureValue
    checkBalances(blocks)
  }

  trait Fixture {
    def insertAndFinalize(blocks: ArraySeq[BlockEntity]): Unit = {
      BlockDao.insertAll(blocks).futureValue
      blocks.foreach { block =>
        BlockDao.updateMainChainStatus(block.hash, true).futureValue
      }

      val endTs = blocks.last.timestamp.plusMillisUnsafe(1)
      FinalizerService
        .finalizeOutputsWith(blocks.head.timestamp, endTs, Duration.ofHoursUnsafe(24))
        .futureValue
    }

    def checkBalances(blocks: ArraySeq[model.BlockEntry]) = {
      val holders = databaseConfig.db.run(getAll).futureValue

      val addresses = blocks.flatMap(_.transactions.flatMap(_.unsigned.fixedOutputs.map(_.address)))

      addresses.distinct.foreach { address =>
        val (balance, _)  = TransactionDao.getBalance(address).futureValue
        val holderBalance = holders.find(_._1 == address).map(_._2).getOrElse(U256.Zero)

        balance is holderBalance
      }
    }

    implicit val blockCache: BlockCache = TestBlockCache()

    val groupIndex      = GroupIndex.Zero
    val chainIndex      = ChainIndex(groupIndex, groupIndex)
    val version: Byte   = 1
    val networkId: Byte = 1
    val scriptOpt       = None

    val defaultBlockEntity: BlockEntity =
      BlockEntity(
        hash = blockHashGen.sample.get,
        timestamp = TimeStamp.unsafe(0),
        chainFrom = groupIndex,
        chainTo = groupIndex,
        height = Height.unsafe(0),
        deps = ArraySeq.empty,
        transactions = ArraySeq.empty,
        inputs = ArraySeq.empty,
        outputs = ArraySeq.empty,
        true,
        nonce = bytesGen.sample.get,
        version = 1.toByte,
        depStateHash = hashGen.sample.get,
        txsHash = hashGen.sample.get,
        target = bytesGen.sample.get,
        hashrate = BigInteger.ZERO,
        ghostUncles = ArraySeq.empty
      )

    def getAll: DBActionSR[(Address, U256)] =
      sql"""
      SELECT
        address,
        balance
      FROM
        holders
      ORDER BY
        balance DESC
    """
        .asAS[(Address, U256)]

  }
}
