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

import java.time.Instant

import scala.concurrent.ExecutionContext

import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Seconds, Span}
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.{AlephiumSpec, Generators, GroupSetting}
import org.alephium.explorer.GenApiModel.transactionHashGen
import org.alephium.explorer.api.model._
import org.alephium.explorer.cache.BlockCache
import org.alephium.explorer.persistence._
import org.alephium.explorer.persistence.dao.BlockDao
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.persistence.schema._
import org.alephium.explorer.persistence.schema.CustomJdbcTypes._
import org.alephium.protocol.ALPH
import org.alephium.util.{Duration, TimeStamp, U256}

@SuppressWarnings(Array("org.wartremover.warts.Var", "org.wartremover.warts.DefaultArguments"))
class TokenSupplyServiceSpec
    extends AlephiumSpec
    with DatabaseFixtureForEach
    with DBRunner
    with Generators
    with ScalaFutures
    with Eventually {
  implicit val executionContext: ExecutionContext = ExecutionContext.global
  override implicit val patienceConfig            = PatienceConfig(timeout = Span(50, Seconds))

  "Build days range" in {
    val launchTime = ALPH.LaunchTimestamp //2021-11-08T11:20:06+00:00
    def ts(str: String): TimeStamp = {
      TimeStamp.unsafe(Instant.parse(str).toEpochMilli)
    }

    TokenSupplyService.buildDaysRange(
      launchTime,
      launchTime.plusUnsafe(Duration.ofHoursUnsafe(8))
    ) is
      Seq.empty

    TokenSupplyService.buildDaysRange(
      launchTime.plusUnsafe(Duration.ofHoursUnsafe(8)),
      launchTime
    ) is
      Seq.empty

    TokenSupplyService.buildDaysRange(
      launchTime,
      launchTime.plusUnsafe(Duration.ofDaysUnsafe(1))
    ) is
      Seq(
        ts("2021-11-08T23:59:59.999Z")
      )

    TokenSupplyService.buildDaysRange(
      launchTime,
      launchTime.plusUnsafe(Duration.ofDaysUnsafe(3))
    ) is
      Seq(
        ts("2021-11-08T23:59:59.999Z"),
        ts("2021-11-09T23:59:59.999Z"),
        ts("2021-11-10T23:59:59.999Z")
      )

    TokenSupplyService.buildDaysRange(
      launchTime,
      ts("2021-11-11T10:39:53.100Z")
    ) is
      Seq(
        ts("2021-11-08T23:59:59.999Z"),
        ts("2021-11-09T23:59:59.999Z"),
        ts("2021-11-10T23:59:59.999Z")
      )
  }

  "Token supply - only genesis - no lock" in new Fixture {
    override val genesisLocked = false

    test(genesisBlock) {
      Seq(U256.Zero)
    }
  }

  "Token supply - only genesis - locked" in new Fixture {
    override val genesisLocked = true

    test(genesisBlock) {
      Seq(U256.Zero)
    }
  }

  "Token supply - block 1 not locked" in new Fixture {
    override val genesisLocked = false

    test(genesisBlock, block1, block2) {
      Seq(
        blockAmount(block1),
        U256.Zero
      )
    }
  }

  "Token supply - block 1 locked" in new Fixture {
    override val genesisLocked = false
    override val block1Locked  = true

    test(genesisBlock, block1, block2) {
      Seq(U256.Zero, U256.Zero)
    }
  }

  "Token supply - some output spent" in new Fixture {
    override val genesisLocked = false

    test(genesisBlock, block1, block2, block3) {
      Seq(blockAmount(block2), blockAmount(block1), U256.Zero)
    }
  }

  "Token supply - genesis locked - some output spent" in new Fixture {
    override val genesisLocked = true

    test(genesisBlock, block1, block2, block3) {
      Seq(blockAmount(block2), blockAmount(block1), U256.Zero)
    }
  }

  "Token supply - Not count excluded addresses" in new Fixture {
    override val genesisLocked = true

    test(genesisBlock, block1, block2, block3, block4) {
      Seq(blockAmount(block2), blockAmount(block2), blockAmount(block1), U256.Zero)
    }
  }

  trait Fixture {

    val now = TimeStamp.now()

    val genesisLocked: Boolean
    val block1Locked: Boolean = false

    implicit val groupSettings: GroupSetting = GroupSetting(1)
    implicit val blockCache: BlockCache      = BlockCache()

    val genesisAddress = Address.unsafe("122uvHwwcaWoXR1ryub9VK1yh2CZvYCqXxzsYDHRb2jYB")

    lazy val genesisBlock = {
      val lockTime =
        if (genesisLocked) Some(TimeStamp.now().plusUnsafe(Duration.ofHoursUnsafe(1))) else None
      val block = blockEntityGen(GroupIndex.unsafe(0), GroupIndex.unsafe(0), None).sample.get
      block.copy(
        outputs = block.outputs.map(
          _.copy(timestamp = block.timestamp, lockTime = lockTime, address = genesisAddress)))
    }

    lazy val block1 = {
      val lockTime =
        if (block1Locked) Some(TimeStamp.now().plusUnsafe(Duration.ofHoursUnsafe(2))) else None
      val timestamp = ALPH.LaunchTimestamp.plusHoursUnsafe(1)
      val block =
        blockEntityGen(GroupIndex.unsafe(0), GroupIndex.unsafe(0), Some(genesisBlock)).sample.get
      block.copy(timestamp = timestamp,
                 outputs   = block.outputs.map(_.copy(timestamp = timestamp, lockTime = lockTime)),
                 inputs    = block.inputs.map(_.copy(timestamp = timestamp)))
    }

    lazy val block2 = {
      val block =
        blockEntityGen(GroupIndex.unsafe(0), GroupIndex.unsafe(0), Some(block1)).sample.get
      val txHash    = transactionHashGen.sample.get
      val timestamp = block.timestamp.plusHoursUnsafe(24)
      block.copy(
        timestamp = timestamp,
        inputs = block1.outputs.zipWithIndex.map {
          case (out, index) =>
            InputEntity(block.hash,
                        txHash,
                        timestamp,
                        0,
                        out.key,
                        None,
                        false,
                        index,
                        0,
                        None,
                        None,
                        None)

        },
        outputs = block.outputs.map(_.copy(timestamp = timestamp, lockTime = None))
      )
    }

    lazy val block3 = {
      val block =
        blockEntityGen(GroupIndex.unsafe(0), GroupIndex.unsafe(0), Some(block2)).sample.get
      val timestamp = block.timestamp.plusHoursUnsafe(24)
      val address =
        Address.unsafe(
          "X4TqZeAizjDV8yt7XzxDVLywdzmJvLALtdAnjAERtCY3TPkyPXt4A5fxvXAX7UucXPpSYF7amNysNiniqb98vQ5rs9gh12MDXhsAf5kWmbmjXDygxV9AboSj8QR7QK8duaKAkZ")
      block.copy(timestamp = timestamp,
                 outputs   = block.outputs.map(_.copy(timestamp = timestamp, address = address)))
    }

    lazy val block4 = {
      val block =
        blockEntityGen(GroupIndex.unsafe(0), GroupIndex.unsafe(0), Some(block3)).sample.get
      val timestamp = block.timestamp.plusHoursUnsafe(24)
      block.copy(timestamp = timestamp, outputs = block.outputs.map(_.copy(timestamp = timestamp)))
    }

    def test(blocks: BlockEntity*)(amounts: Seq[U256]) = {
      BlockDao.insertAll(Seq.from(blocks)).futureValue
      blocks.foreach { block =>
        BlockDao.updateMainChainStatus(block.hash, true).futureValue
      }

      TokenSupplyService.syncOnce().futureValue is ()

      eventually {
        val tokenSupply =
          run(TokenSupplySchema.table.sortBy(_.timestamp).result).futureValue.reverse

        tokenSupply.map(_.circulating) is amounts

        TokenSupplyService
          .listTokenSupply(Pagination.unsafe(0, 1))
          .futureValue
          .map(_.circulating) is Seq(amounts.head)
        TokenSupplyService
          .listTokenSupply(Pagination.unsafe(0, 0))
          .futureValue is Seq.empty

        TokenSupplyService
          .getLatestTokenSupply()
          .futureValue
          .map(_.circulating) is Some(amounts.head)
      }

      databaseConfig.db.close
    }

    def blockAmount(blockEntity: BlockEntity): U256 =
      blockEntity.outputs.map(_.amount).fold(U256.Zero)(_ addUnsafe _)
  }
}
