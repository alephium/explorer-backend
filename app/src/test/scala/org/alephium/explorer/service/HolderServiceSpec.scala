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
import scala.concurrent.Future
import scala.jdk.CollectionConverters._

import slick.jdbc.PostgresProfile.api._
import io.vertx.core.buffer.Buffer
import org.scalacheck.Gen

import org.alephium.explorer.persistence._
import org.alephium.explorer.persistence.schema.CustomGetResult._
import org.alephium.explorer.persistence.schema.CustomSetParameter._
import org.alephium.explorer.util.SlickUtil._
import org.alephium.api.UtilJson._
import org.alephium.explorer.AlephiumActorSpecLike
import org.alephium.explorer.ConfigDefaults._
import org.alephium.explorer.GenApiModel._
import org.alephium.explorer.GenCoreApi._
import org.alephium.explorer.GenCoreProtocol._
import org.alephium.explorer.GenCoreUtil._
import org.alephium.explorer.GenDBModel._
import org.alephium.explorer.api.model._
import org.alephium.explorer.cache.{BlockCache, TestBlockCache}
import org.alephium.explorer.persistence.DatabaseFixtureForEach
import org.alephium.explorer.persistence.dao._
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.persistence.queries._
import org.alephium.json.Json._
import org.alephium.protocol.ALPH
import org.alephium.protocol.model.{Address, BlockHash, ChainIndex, GroupIndex}
import org.alephium.util.{Duration, TimeStamp, U256}

@SuppressWarnings(
  Array(
    "org.wartremover.warts.Var",
    "org.wartremover.warts.DefaultArguments",
    "org.wartremover.warts.AsInstanceOf"
  )
)
class HolderServiceSpec extends AlephiumActorSpecLike with DatabaseFixtureForEach {

  "insert initial table" in new Fixture {
    val blocks = chainGen(4, TimeStamp.now(), ChainIndex.unsafe(0, 0)).sample.get
    val blockEntities: Seq[BlockEntity] = blocks.map(BlockFlowClient.blockProtocolToEntity)

    val ts = blockEntities.map(_.timestamp)
    println(s"${Console.RED}${Console.BOLD}*** ts ***${Console.RESET}${ts}")
    BlockDao.insertAll(blockEntities).futureValue
    blockEntities.foreach { block =>
      BlockDao.updateMainChainStatus(block.hash, true).futureValue
    }

    val endTs = blocks.last.timestamp.plusMillisUnsafe(1234)
    println(s"${Console.RED}${Console.BOLD}*** endTs ***${Console.RESET}${endTs}")
    FinalizerService
      .finalizeOutputsWith(blocks.head.timestamp, endTs, Duration.ofHoursUnsafe(24))
      .futureValue
    HolderService.syncOnce().futureValue

    val holders = databaseConfig.db.run(getAll).futureValue

    // println(s"${Console.RED}${Console.BOLD}*** blocks ***${Console.RESET}${blocks}")
    val addresses = blocks.flatMap(_.transactions.flatMap(_.unsigned.fixedOutputs.map(_.address)))
    // println(s"${Console.RED}${Console.BOLD}*** addresses ***${Console.RESET}${addresses.distinct.length}")

    addresses.distinct.foreach { address =>
      // println(s"${Console.RED}${Console.BOLD}*** address ***${Console.RESET}${address}")
      val (balance, locked) = TransactionDao.getBalance(address).futureValue
      // println(s"${Console.RED}${Console.BOLD}*** balance ***${Console.RESET}${balance}")
      // println(s"${Console.RED}${Console.BOLD}*** locked ***${Console.RESET}${locked}")
      val holderBalance = holders.find(_._1 == address).map(_._2).getOrElse(U256.Zero)
      // println(s"${Console.RED}${Console.BOLD}*** holderBalance ***${Console.RESET}${holderBalance}")

      balance is holderBalance
    }
    val outputRefs = blocks.flatMap(_.transactions.flatMap(_.unsigned.inputs.map(_.outputRef.key)))

    val outputs = blocks
      .flatMap(_.transactions.flatMap(_.unsigned.fixedOutputs))
      .filter(output => outputRefs.contains(output.key))
  }

  trait Fixture {
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

  trait TxsByAddressFixture extends Fixture {
    val address = addressGen.sample.get

    val halfDay = Duration.ofHoursUnsafe(12)
    val now     = TimeStamp.now()

    val blocks = Gen
      .listOfN(5, blockEntityGen(chainIndex))
      .map(_.zipWithIndex.map { case (block, index) =>
        val timestamp = now + (halfDay.timesUnsafe(index.toLong))
        block.copy(
          timestamp = timestamp,
          outputs = block.outputs.map(_.copy(address = address, timestamp = timestamp)),
          inputs =
            block.inputs.map(_.copy(outputRefAddress = Some(address), timestamp = timestamp)),
          transactions = block.transactions.map { tx =>
            tx.copy(timestamp = timestamp)
          }
        )
      })
      .sample
      .get

    BlockDao.insertAll(blocks).futureValue
    Future
      .sequence(blocks.map { block =>
        for {
          _ <- databaseConfig.db.run(InputUpdateQueries.updateInputs())
          _ <- BlockDao.updateMainChainStatus(block.hash, true)
        } yield (())
      })
      .futureValue

    val transactions = blocks.flatMap(_.transactions).sortBy(_.timestamp)
    val timestamps   = transactions.map(_.timestamp).distinct
    val fromTs       = timestamps.head
    val toTs         = timestamps.last + Duration.ofMillisUnsafe(1)
  }
}
