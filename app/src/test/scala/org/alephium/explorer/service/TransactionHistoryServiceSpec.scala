// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.service

import java.time.Instant

import scala.collection.immutable.ArraySeq

import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.AlephiumFutureSpec
import org.alephium.explorer.ConfigDefaults._
import org.alephium.explorer.GenDBModel._
import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence.{DatabaseFixtureForEach, DBRunner}
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.persistence.schema._
import org.alephium.protocol.model.{ChainIndex, GroupIndex}
import org.alephium.util._

class TransactionHistoryServiceSpec
    extends AlephiumFutureSpec
    with DatabaseFixtureForEach
    with DBRunner {

  def ts(str: String): TimeStamp = {
    TimeStamp.unsafe(Instant.parse(str).toEpochMilli)
  }

  "getTimeRanges" should {
    "build daily time ranges" in {
      TransactionHistoryService.getTimeRanges(
        ts("2022-01-08T09:54:32.101Z"),
        ts("2022-01-10T12:34:56.789Z"),
        IntervalType.Daily
      ) is
        ArraySeq(
          (ts("2022-01-08T00:00:00.000Z"), ts("2022-01-08T23:59:59.999Z")),
          (ts("2022-01-09T00:00:00.000Z"), ts("2022-01-09T23:59:59.999Z"))
          // 2022-01-10 isn't done, so we don't count it
        )
    }

    "build hourly time ranges" in {
      TransactionHistoryService.getTimeRanges(
        ts("2022-01-08T09:54:32.101Z"),
        ts("2022-01-08T12:34:56.789Z"),
        IntervalType.Hourly
      ) is
        ArraySeq(
          (ts("2022-01-08T09:00:00.000Z"), ts("2022-01-08T09:59:59.999Z")),
          (ts("2022-01-08T10:00:00.000Z"), ts("2022-01-08T10:59:59.999Z")),
          (ts("2022-01-08T11:00:00.000Z"), ts("2022-01-08T11:59:59.999Z"))
          // 12:34:56 isn't done, so we don't count it
        )
    }

    "return empty range when start is after end" in {

      TransactionHistoryService.getTimeRanges(
        ts("2022-01-08T00:00:00.000Z"),
        ts("2022-01-07T00:00:00.000Z"),
        IntervalType.Hourly
      ) is ArraySeq.empty
    }

    "return empty range when end == start" in {

      TransactionHistoryService.getTimeRanges(
        ts("2022-01-08T00:00:00.000Z"),
        ts("2022-01-08T00:00:00.000Z"),
        IntervalType.Hourly
      ) is ArraySeq.empty
    }
  }

  "countAndInsert" should {
    "handle per chains and all chains counting" in {

      val group0 = GroupIndex.Zero
      val group1 = new GroupIndex(1)

      // Launch timestamp: 2021-11-08T13:59:33.00Z

      def txGroup(tsStr: String, group: GroupIndex): TransactionEntity = {
        transactionEntityGen().sample.get
          .copy(timestamp = ts(tsStr), chainFrom = group, chainTo = group, mainChain = true)
      }

      val tx1 = txGroup("2021-11-08T14:15:00.000Z", group0)
      val tx2 = txGroup("2021-11-08T14:23:00.000Z", group0)
      val tx3 = txGroup("2021-11-08T14:32:00.000Z", group1)
      val tx4 = txGroup("2021-11-08T14:45:00.000Z", group1)
      val tx5 = txGroup("2021-11-08T15:56:00.000Z", group0)

      val tx6  = txGroup("2021-11-09T08:08:00.000Z", group0)
      val tx7  = txGroup("2021-11-09T08:23:00.000Z", group1)
      val tx8  = txGroup("2021-11-09T08:54:00.000Z", group0)
      val tx9  = txGroup("2021-11-09T12:43:00.000Z", group0)
      val tx10 = txGroup("2021-11-09T12:58:00.000Z", group0)

      val tx11 = txGroup("2021-11-10T01:00:00.000Z", group0)

      val txs = ArraySeq(
        tx1,
        tx2,
        tx3,
        tx4,
        tx5,
        tx6,
        tx7,
        tx8,
        tx9,
        tx10,
        tx11
      )

      val blocks = txs.map { tx =>
        blockHeaderGen.sample.get
          .copy(
            hash = tx.blockHash,
            mainChain = tx.mainChain,
            timestamp = tx.timestamp,
            chainFrom = tx.chainFrom,
            chainTo = tx.chainTo,
            txsCount = 1
          )
      }

      val latestBlocks = blocks
        .groupBy(block => (block.chainFrom, block.chainTo))
        .view
        .mapValues(_.maxBy(_.timestamp))
        .values
        .map { block =>
          LatestBlock.fromEntity(
            blockEntityGen(ChainIndex.unsafe(block.chainFrom.value, block.chainTo.value)).sample.get
              .copy(timestamp = block.timestamp)
          )
        }

      run(
        for {
          _ <- BlockHeaderSchema.table ++= blocks
          _ <- TransactionSchema.table ++= txs
          _ <- LatestBlockSchema.table ++= latestBlocks
        } yield ()
      ).futureValue

      TransactionHistoryService.syncOnce().futureValue

      /*
       * Per Chain Daily
       */

      val perChainsDaily = TransactionHistoryService
        .getPerChain(
          ts("2021-11-07T12:34:00.000Z"),
          ts("2021-11-09T23:00:00.000Z"),
          IntervalType.Daily
        )
        .futureValue

      perChainsDaily.map { perChainTime =>
        PerChainTimedCount(
          perChainTime.timestamp,
          perChainTime.totalCountPerChain.filter(_.count != 0)
        )
      } is
        ArraySeq(
          PerChainTimedCount(
            ts("2021-11-08T00:00:00.000Z"),
            ArraySeq(
              PerChainCount(group0.value, group0.value, 3),
              PerChainCount(group1.value, group1.value, 2)
            )
          ),
          PerChainTimedCount(
            ts("2021-11-09T00:00:00.000Z"),
            ArraySeq(
              PerChainCount(group0.value, group0.value, 4),
              PerChainCount(group1.value, group1.value, 1)
            )
          )
        )

      /*
       * Per Chain Hourly
       */

      val perChainsHourly = TransactionHistoryService
        .getPerChain(
          ts("2021-11-07T12:34:00.000Z"),
          ts("2021-11-09T23:00:00.000Z"),
          IntervalType.Hourly
        )
        .futureValue

      perChainsHourly
        .map { perChainTime =>
          PerChainTimedCount(
            perChainTime.timestamp,
            perChainTime.totalCountPerChain.filter(_.count != 0)
          )
        }
        .filter(_.totalCountPerChain.nonEmpty) is
        ArraySeq(
          PerChainTimedCount(
            ts("2021-11-08T14:00:00.000Z"),
            ArraySeq(
              PerChainCount(group0.value, group0.value, 2),
              PerChainCount(group1.value, group1.value, 2)
            )
          ),
          PerChainTimedCount(
            ts("2021-11-08T15:00:00.000Z"),
            ArraySeq(PerChainCount(group0.value, group0.value, 1))
          ),
          PerChainTimedCount(
            ts("2021-11-09T08:00:00.000Z"),
            ArraySeq(
              PerChainCount(group0.value, group0.value, 2),
              PerChainCount(group1.value, group1.value, 1)
            )
          ),
          PerChainTimedCount(
            ts("2021-11-09T12:00:00.000Z"),
            ArraySeq(PerChainCount(group0.value, group0.value, 2))
          )
        )

      /*
       * All Chains Daily
       */

      val allChainsDaily = TransactionHistoryService
        .getAllChains(
          ts("2021-11-07T12:34:00.000Z"),
          ts("2021-11-09T23:00:00.000Z"),
          IntervalType.Daily
        )
        .futureValue

      allChainsDaily is
        ArraySeq(
          (ts("2021-11-08T00:00:00.000Z"), 5L),
          (ts("2021-11-09T00:00:00.000Z"), 5L)
        )

      /*
       * All Chains Hourly
       */

      val allChainsHourly = TransactionHistoryService
        .getAllChains(
          ts("2021-11-07T12:34:00.000Z"),
          ts("2021-11-09T23:00:00.000Z"),
          IntervalType.Hourly
        )
        .futureValue

      allChainsHourly.filter(_._2 != 0) is
        ArraySeq(
          (ts("2021-11-08T14:00:00.000Z"), 4L),
          (ts("2021-11-08T15:00:00.000Z"), 1L),
          (ts("2021-11-09T08:00:00.000Z"), 3L),
          (ts("2021-11-09T12:00:00.000Z"), 2L)
        )
    }
  }
}
