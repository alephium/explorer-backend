// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.service

import java.time.Instant

import scala.collection.immutable.ArraySeq

import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.{AlephiumFutureSpec, GroupSetting}
import org.alephium.explorer.GenDBModel._
import org.alephium.explorer.api.model._
import org.alephium.explorer.cache.{BlockCache, TestBlockCache}
import org.alephium.explorer.persistence._
import org.alephium.explorer.persistence.dao.BlockDao
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.persistence.schema._
import org.alephium.explorer.persistence.schema.CustomJdbcTypes._
import org.alephium.protocol.ALPH
import org.alephium.protocol.model.{Address, ChainIndex, GroupIndex}
import org.alephium.util.{Duration, TimeStamp, U256}

@SuppressWarnings(Array("org.wartremover.warts.Var", "org.wartremover.warts.DefaultArguments"))
class TokenSupplyServiceSpec
    extends AlephiumFutureSpec
    with DatabaseFixtureForEach
    with TestDBRunner {

  implicit val gs: GroupSetting = GroupSetting(1)

  "Build days range" in {
    val launchTime = ALPH.LaunchTimestamp // 2021-11-08T11:20:06+00:00
    def ts(str: String): TimeStamp = {
      TimeStamp.unsafe(Instant.parse(str).toEpochMilli)
    }

    TokenSupplyService.buildDaysRange(
      launchTime,
      launchTime.plusUnsafe(Duration.ofHoursUnsafe(8))
    ) is
      ArraySeq.empty

    TokenSupplyService.buildDaysRange(
      launchTime.plusUnsafe(Duration.ofHoursUnsafe(8)),
      launchTime
    ) is
      ArraySeq.empty

    TokenSupplyService.buildDaysRange(
      launchTime,
      launchTime.plusUnsafe(Duration.ofDaysUnsafe(1))
    ) is
      ArraySeq(
        ts("2021-11-08T23:59:59.999Z")
      )

    TokenSupplyService.buildDaysRange(
      launchTime,
      launchTime.plusUnsafe(Duration.ofDaysUnsafe(3))
    ) is
      ArraySeq(
        ts("2021-11-08T23:59:59.999Z"),
        ts("2021-11-09T23:59:59.999Z"),
        ts("2021-11-10T23:59:59.999Z")
      )

    TokenSupplyService.buildDaysRange(
      launchTime,
      ts("2021-11-11T10:39:53.100Z")
    ) is
      ArraySeq(
        ts("2021-11-08T23:59:59.999Z"),
        ts("2021-11-09T23:59:59.999Z"),
        ts("2021-11-10T23:59:59.999Z")
      )
  }

  "Token supply - only genesis - no lock" in new Fixture {
    override val genesisLocked = false

    test(genesisBlock) {
      ArraySeq(U256.Zero)
    }
  }

  "Token supply - only genesis - locked" in new Fixture {
    override val genesisLocked = true

    test(genesisBlock) {
      ArraySeq(U256.Zero)
    }
  }

  "Token supply - block 1 not locked" in new Fixture {
    override val genesisLocked = false

    test(genesisBlock, block1, block2) {
      ArraySeq(
        blockAmount(block1),
        U256.Zero
      )
    }
  }

  "Token supply - block 1 locked" in new Fixture {
    override val genesisLocked = false
    override val block1Locked  = true

    test(genesisBlock, block1, block2) {
      ArraySeq(U256.Zero, U256.Zero)
    }
  }

  "Token supply - some output spent" in new Fixture {
    override val genesisLocked = false

    test(genesisBlock, block1, block2, block3) {
      ArraySeq(blockAmount(block2), blockAmount(block1), U256.Zero)
    }
  }

  "Token supply - genesis locked - some output spent" in new Fixture {
    override val genesisLocked = true

    test(genesisBlock, block1, block2, block3) {
      ArraySeq(blockAmount(block2), blockAmount(block1), U256.Zero)
    }
  }

  "Token supply - Not count excluded addresses" in new Fixture {
    override val genesisLocked = true

    test(genesisBlock, block1, block2, block3, block4) {
      ArraySeq(blockAmount(block2), blockAmount(block2), blockAmount(block1), U256.Zero)
    }
  }

  trait Fixture {

    val now = TimeStamp.now()

    val genesisLocked: Boolean
    val block1Locked: Boolean = false

    val chainIndex = ChainIndex(GroupIndex.Zero, GroupIndex.Zero)

    implicit val blockCache: BlockCache = TestBlockCache()(gs, executionContext, databaseConfig)

    val genesisAddress =
      Address.fromBase58("122uvHwwcaWoXR1ryub9VK1yh2CZvYCqXxzsYDHRb2jYB").rightValue

    lazy val genesisBlock = {
      val lockTime =
        if (genesisLocked) Some(TimeStamp.now().plusUnsafe(Duration.ofHoursUnsafe(1))) else None
      val block =
        blockEntityWithParentGen(chainIndex, None).sample.get
      block.copy(
        transactions = block.transactions.map(_.copy(timestamp = block.timestamp)),
        outputs = block.outputs.map(
          _.copy(timestamp = block.timestamp, lockTime = lockTime, address = genesisAddress)
        )
      )
    }

    lazy val block1 = {
      val lockTime =
        if (block1Locked) Some(TimeStamp.now().plusUnsafe(Duration.ofHoursUnsafe(2))) else None
      val timestamp = ALPH.LaunchTimestamp.plusHoursUnsafe(1)
      val block =
        blockEntityWithParentGen(chainIndex, Some(genesisBlock)).sample.get
      block.copy(
        timestamp = timestamp,
        outputs = block.outputs.map(_.copy(timestamp = timestamp, lockTime = lockTime)),
        transactions = block.transactions.map(_.copy(timestamp = timestamp)),
        inputs = block.inputs.map(_.copy(timestamp = timestamp))
      )
    }

    lazy val block2 = {
      val block =
        blockEntityWithParentGen(chainIndex, Some(block1)).sample.get
      val txHash    = block.transactions.head.hash
      val timestamp = block.timestamp.plusHoursUnsafe(24)
      block.copy(
        timestamp = timestamp,
        inputs = block1.outputs.zipWithIndex.map { case (out, index) =>
          InputEntity(
            block.hash,
            txHash,
            timestamp,
            0,
            out.key,
            None,
            false,
            conflicted = None,
            index,
            0,
            None,
            None,
            None,
            None,
            None,
            contractInput = false
          )

        },
        transactions = block.transactions.map(_.copy(timestamp = timestamp)),
        outputs = block.outputs.map(_.copy(timestamp = timestamp, lockTime = None))
      )
    }

    lazy val block3 = {
      val block =
        blockEntityWithParentGen(chainIndex, Some(block2)).sample.get
      val timestamp = block.timestamp.plusHoursUnsafe(24)
      val address =
        Address
          .fromBase58(
            "X4TqZeAizjDV8yt7XzxDVLywdzmJvLALtdAnjAERtCY3TPkyPXt4A5fxvXAX7UucXPpSYF7amNysNiniqb98vQ5rs9gh12MDXhsAf5kWmbmjXDygxV9AboSj8QR7QK8duaKAkZ"
          )
          .rightValue
      block.copy(
        timestamp = timestamp,
        transactions = block.transactions.map(_.copy(timestamp = timestamp)),
        outputs = block.outputs.map(_.copy(timestamp = timestamp, address = address))
      )
    }

    lazy val block4 = {
      val block =
        blockEntityWithParentGen(chainIndex, Some(block3)).sample.get
      val timestamp = block.timestamp.plusHoursUnsafe(24)
      block.copy(
        timestamp = timestamp,
        transactions = block.transactions.map(_.copy(timestamp = timestamp)),
        outputs = block.outputs.map(_.copy(timestamp = timestamp))
      )
    }

    def test(blocks: BlockEntity*)(amounts: ArraySeq[U256]) = {
      BlockDao.insertAll(ArraySeq.from(blocks))(executionContext, databaseConfig, gs).futureValue
      blocks.foreach { block =>
        BlockDao.updateMainChainStatus(block.hash, true).futureValue
      }

      val latestBlocks = blocks
        .groupBy(block => (block.chainFrom, block.chainTo))
        .view
        .mapValues(_.maxBy(_.timestamp))
        .values
        .map(block => LatestBlock.fromEntity(block))

      exec(LatestBlockSchema.table ++= latestBlocks)

      val timestamps = blocks.map(_.timestamp)
      val minTs      = timestamps.min
      val maxTs      = timestamps.max

      FinalizerService
        .finalizeOutputsWith(timestamps.min, timestamps.max, maxTs.deltaUnsafe(minTs))
        .futureValue is ()
      TokenSupplyService.syncOnce()(executionContext, databaseConfig, gs).futureValue is ()

      eventually {
        val tokenSupply =
          exec(TokenSupplySchema.table.sortBy(_.timestamp).result).reverse

        tokenSupply.map(_.circulating) is amounts

        TokenSupplyService
          .listTokenSupply(Pagination.unsafe(1, 1))
          .futureValue
          .map(_.circulating) is ArraySeq(amounts.head)
        TokenSupplyService
          .listTokenSupply(Pagination.unsafe(1, 0))
          .futureValue is ArraySeq.empty

        TokenSupplyService
          .getLatestTokenSupply()
          .futureValue
          .map(_.circulating) is Some(amounts.head)
      }
    }

    def blockAmount(blockEntity: BlockEntity): U256 =
      blockEntity.outputs.map(_.amount).fold(U256.Zero)(_ addUnsafe _)
  }
}
