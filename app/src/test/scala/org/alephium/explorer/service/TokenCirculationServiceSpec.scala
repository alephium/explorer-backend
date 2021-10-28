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

import scala.concurrent.ExecutionContext

import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Seconds, Span}

import org.alephium.explorer.{AlephiumSpec, Generators}
import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence._
import org.alephium.explorer.persistence.dao.BlockDao
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.persistence.schema._
import org.alephium.util.{Duration, TimeStamp, U256}

@SuppressWarnings(Array("org.wartremover.warts.Var", "org.wartremover.warts.DefaultArguments"))
class TokenCirculationServiceSpec extends AlephiumSpec with ScalaFutures with Eventually {
  implicit val executionContext: ExecutionContext = ExecutionContext.global
  override implicit val patienceConfig            = PatienceConfig(timeout = Span(50, Seconds))

  it should "Token circulation - only genesis - no lock" in new Fixture {
    override val genesisLocked = false

    test(genesisBlock) {
      genesisBlock.outputs
        .map(_.amount)
        .fold(U256.Zero)(_ addUnsafe _)
    }
  }

  it should "Token circulation - only genesis - locked" in new Fixture {
    override val genesisLocked = true

    test(genesisBlock) {
      U256.Zero
    }
  }

  it should "Token circulation - block 1 not locked" in new Fixture {
    override val genesisLocked = false

    test(genesisBlock, block1) {
      blockAmount(genesisBlock).addUnsafe(blockAmount(block1))
    }
  }

  it should "Token circulation - block 1 locked" in new Fixture {
    override val genesisLocked = false
    override val block1Locked  = true

    test(genesisBlock, block1) {
      blockAmount(genesisBlock).addUnsafe(blockAmount(block1))
    }
  }

  it should "Token circulation - some output spent" in new Fixture {
    override val genesisLocked = false

    test(genesisBlock, block1, block2) {
      blockAmount(genesisBlock).addUnsafe(blockAmount(block2))
    }
  }

  it should "Token circulation - genesis locked - some output spent" in new Fixture {
    override val genesisLocked = true

    test(genesisBlock, block1, block2) {
      blockAmount(block2)
    }
  }

  trait Fixture extends TokenCirculationSchema with DatabaseFixture with DBRunner with Generators {
    override val config = databaseConfig
    import config.profile.api._

    val now = TimeStamp.now()

    val genesisLocked: Boolean
    val block1Locked: Boolean = false

    lazy val blockDao: BlockDao = BlockDao(databaseConfig)

    lazy val tokenCirculationService: TokenCirculationService =
      TokenCirculationService(syncPeriod = Duration.unsafe(30 * 1000), databaseConfig)

    lazy val genesisBlock = {
      val lockTime =
        if (genesisLocked) Some(TimeStamp.now().plusUnsafe(Duration.ofHoursUnsafe(1))) else None
      val block = blockEntityGen(GroupIndex.unsafe(0), GroupIndex.unsafe(0), None).sample.get
      block.copy(outputs = block.outputs.map(_.copy(lockTime = lockTime)))
    }

    lazy val block1 = {
      val lockTime =
        if (block1Locked) Some(TimeStamp.now().plusUnsafe(Duration.ofHoursUnsafe(1))) else None
      val block =
        blockEntityGen(GroupIndex.unsafe(0), GroupIndex.unsafe(0), Some(genesisBlock)).sample.get
      block.copy(outputs = block.outputs.map(_.copy(lockTime = lockTime)))
    }

    lazy val block2 = {
      val block =
        blockEntityGen(GroupIndex.unsafe(0), GroupIndex.unsafe(0), Some(block1)).sample.get
      val txHash = transactionHashGen.sample.get
      block.copy(inputs = block1.outputs.map(out =>
        InputEntity(block.hash, txHash, block.timestamp, 0, out.key, None, false)))
    }

    def test(blocks: BlockEntity*)(amount: U256) = {
      blockDao.insertAll(Seq.from(blocks)).futureValue
      blocks.foreach { block =>
        blockDao.updateMainChainStatus(block.hash, true).futureValue
      }

      tokenCirculationService.syncOnce().futureValue is ()

      eventually {
        val tokenCirculation = run(tokenCirculationTable.result).futureValue
        tokenCirculation.head.amount is amount
      }

      databaseConfig.db.close
    }

    def blockAmount(blockEntity: BlockEntity): U256 =
      blockEntity.outputs.map(_.amount).fold(U256.Zero)(_ addUnsafe _)
  }
}
