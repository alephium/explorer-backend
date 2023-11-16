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

import io.vertx.core.buffer.Buffer
import org.scalacheck.Gen

import org.alephium.api.UtilJson._
import org.alephium.explorer.AlephiumActorSpecLike
import org.alephium.explorer.ConfigDefaults._
import org.alephium.explorer.GenApiModel._
import org.alephium.explorer.GenCoreProtocol._
import org.alephium.explorer.GenDBModel._
import org.alephium.explorer.api.model._
import org.alephium.explorer.cache.{BlockCache, TestBlockCache}
import org.alephium.explorer.persistence.DatabaseFixtureForEach
import org.alephium.explorer.persistence.dao.BlockDao
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.persistence.queries.InputUpdateQueries
import org.alephium.json.Json._
import org.alephium.protocol.model.{ChainIndex, GroupIndex}
import org.alephium.util.{Duration, TimeStamp}

@SuppressWarnings(
  Array(
    "org.wartremover.warts.Var",
    "org.wartremover.warts.DefaultArguments",
    "org.wartremover.warts.AsInstanceOf"
  )
)
class TokenServiceSpec extends AlephiumActorSpecLike with DatabaseFixtureForEach {

  "get amount history" in new Fixture {
    Seq[IntervalType](IntervalType.Hourly, IntervalType.Daily).foreach { intervalType =>
      val token = tokens.head
      val flowable = TokenService
        .getAmountHistory(address, token.id, fromTs, toTs, intervalType, 8)

      val result: Seq[Buffer] =
        flowable.toList().blockingGet().asScala

      val amountHistory = read[ujson.Value](result.mkString)
      val history       = read[Seq[(Long, BigInteger)]](amountHistory("amountHistory"))

      val times = history.map(_._1)

      // Test that history is always ordered correctly
      times is times.sorted

      // TODO Test history amount value
    }
  }

  trait Fixture {
    implicit val blockCache: BlockCache = TestBlockCache()

    val groupIndex = GroupIndex.Zero
    val chainIndex = ChainIndex(groupIndex, groupIndex)

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
        hashrate = BigInteger.ZERO
      )

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
    val tokens       = blocks.flatMap(_.outputs.flatMap(_.tokens)).flatten
  }
}
