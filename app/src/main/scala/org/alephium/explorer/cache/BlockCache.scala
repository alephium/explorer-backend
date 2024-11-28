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

package org.alephium.explorer.cache

import scala.collection.immutable.ArraySeq
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.jdk.FutureConverters._

import com.github.benmanes.caffeine.cache.{AsyncCacheLoader, Caffeine}
import io.prometheus.metrics.core.metrics.Gauge
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.GroupSetting
import org.alephium.explorer.persistence.DBRunner._
import org.alephium.explorer.persistence.model.{AppState, LatestBlock}
import org.alephium.explorer.persistence.queries.{AppStateQueries, BlockQueries}
import org.alephium.explorer.persistence.schema.CustomGetResult.lastFinalizedInputTimeGetResult
import org.alephium.protocol.config.GroupConfig
import org.alephium.protocol.model.ChainIndex
import org.alephium.util.{Duration, TimeStamp}

object BlockCache {

  @SuppressWarnings(Array("org.wartremover.warts.IterableOps"))
  private def computeAverageBlockTime(blockTimes: ArraySeq[TimeStamp]): Duration = {
    if (blockTimes.sizeIs > 1) {
      val (_, diffs) =
        blockTimes.drop(1).foldLeft((blockTimes.head, ArraySeq.empty: ArraySeq[Duration])) {
          case ((prev, acc), ts) =>
            (ts, acc :+ ts.deltaUnsafe(prev))
        }
      diffs.fold(Duration.zero)(_ + _).divUnsafe(diffs.size.toLong)
    } else {
      Duration.zero
    }
  }

  // scalastyle:off
  def apply(
      cacheRowCountReloadPeriod: FiniteDuration,
      cacheBlockTimesReloadPeriod: FiniteDuration,
      cacheLatestBlocksReloadPeriod: FiniteDuration
  )(implicit
      groupSetting: GroupSetting,
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile]
  ): BlockCache = {
    implicit val groupConfig: GroupConfig = groupSetting.groupConfig

    /*
     * `Option.get` is used to avoid unnecessary memory allocations.
     * This cache is guaranteed to have data available for
     * all chain-indexes after a single run of sync so `.get` would
     * never fail after first few seconds after boot-up.
     *
     * @see Comments in PR <a href="https://github.com/alephium/explorer-backend/pull/393">#393</a>
     */
    @SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
    val latestBlockAsyncLoader: AsyncCacheLoader[ChainIndex, LatestBlock] = { case (key, _) =>
      run(
        BlockQueries.getLatestBlock(key.from, key.to)
      ).map(_.headOption.getOrElse(LatestBlock.empty())).asJava.toCompletableFuture
    }

    val cachedLatestBlocks: CaffeineAsyncCache[ChainIndex, LatestBlock] =
      CaffeineAsyncCache {
        Caffeine
          .newBuilder()
          .maximumSize(groupConfig.chainNum.toLong)
          .expireAfterWrite(
            cacheLatestBlocksReloadPeriod.toNanos,
            java.util.concurrent.TimeUnit.NANOSECONDS
          )
          .buildAsync[ChainIndex, LatestBlock](latestBlockAsyncLoader)
      }

    val blockTimeAsyncLoader: AsyncCacheLoader[ChainIndex, Duration] = { case (key, _) =>
      val chainFrom = key.from
      val chainTo   = key.to
      (for {
        latestBlock <- cachedLatestBlocks.get(key)
        after = latestBlock.timestamp.minusUnsafe(Duration.ofHoursUnsafe(2))
        blockTimes <- run(BlockQueries.getBlockTimes(chainFrom, chainTo, after))
      } yield {
        computeAverageBlockTime(blockTimes)
      }).asJava.toCompletableFuture
    }

    val cachedBlockTimes: CaffeineAsyncCache[ChainIndex, Duration] =
      CaffeineAsyncCache {
        Caffeine
          .newBuilder()
          .maximumSize(groupConfig.chainNum.toLong)
          .expireAfterWrite(
            cacheBlockTimesReloadPeriod.toNanos,
            java.util.concurrent.TimeUnit.NANOSECONDS
          )
          .buildAsync[ChainIndex, Duration](blockTimeAsyncLoader)
      }

    val cacheRowCount: AsyncReloadingCache[Int] =
      AsyncReloadingCache(0, cacheRowCountReloadPeriod) { _ =>
        run(BlockQueries.mainChainQuery.length.result)
      }

    val latestFinalizeTime: AsyncReloadingCache[TimeStamp] =
      AsyncReloadingCache(TimeStamp.zero, 5.minutes) { _ =>
        run(AppStateQueries.get(AppState.LastFinalizedInputTime))
          .map(_.map(_.time).getOrElse(TimeStamp.zero))
      }

    latestFinalizeTime.expireAndReload()

    new BlockCache(
      blockTimes = cachedBlockTimes,
      rowCount = cacheRowCount,
      latestBlocks = cachedLatestBlocks,
      lastFinalizedTimestamp = latestFinalizeTime
    )
  }

  val latestBlocksSynced: Gauge = Gauge
    .builder()
    .name(
      "alephimum_explorer_backend_latest_blocks_synced"
    )
    .help(
      "Latest blocks synced per chainindex"
    )
    .labelNames("chain_from", "chain_to")
    .register()
}

/** Cache used by Block queries.
  *
  * Encapsulate so the cache mutation is not directly accessible by clients.
  */
class BlockCache(
    blockTimes: CaffeineAsyncCache[ChainIndex, Duration],
    rowCount: AsyncReloadingCache[Int],
    latestBlocks: CaffeineAsyncCache[ChainIndex, LatestBlock],
    lastFinalizedTimestamp: AsyncReloadingCache[TimeStamp]
)(implicit groupConfig: GroupConfig) {

  private val latestBlocksSyncedLabeled =
    groupConfig.cliqueChainIndexes.map(chainIndex =>
      BlockCache.latestBlocksSynced
        .labelValues(chainIndex.from.value.toString, chainIndex.to.value.toString)
    )

  /** Operations on `blockTimes` cache */
  def getAllBlockTimes(chainIndexes: Iterable[ChainIndex])(implicit
      ec: ExecutionContext
  ): Future[ArraySeq[(ChainIndex, Duration)]] =
    blockTimes.getAll(chainIndexes)

  /** Operations on `latestBlocks` cache */
  def getAllLatestBlocks()(implicit
      ec: ExecutionContext,
      groupSetting: GroupSetting
  ): Future[ArraySeq[(ChainIndex, LatestBlock)]] =
    latestBlocks.getAll(groupSetting.chainIndexes)

  def putLatestBlock(chainIndex: ChainIndex, block: LatestBlock): Unit = {
    latestBlocks.put(chainIndex, block)
    latestBlocksSyncedLabeled
      .get(chainIndex.flattenIndex)
      .foreach(_.set(block.height.value.toDouble))
  }

  def getMainChainBlockCount(): Int =
    rowCount.get()

  def getLastFinalizedTimestamp(): TimeStamp =
    lastFinalizedTimestamp.get()
}
