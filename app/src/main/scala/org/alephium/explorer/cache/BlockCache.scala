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

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.jdk.FutureConverters._

import com.github.benmanes.caffeine.cache.{AsyncCacheLoader, Caffeine}
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.GroupSetting
import org.alephium.explorer.api.model.GroupIndex
import org.alephium.explorer.persistence.DBRunner._
import org.alephium.explorer.persistence.model.LatestBlock
import org.alephium.explorer.persistence.queries.BlockQueries
import org.alephium.protocol.config.GroupConfig
import org.alephium.protocol.model.ChainIndex
import org.alephium.util.{Duration, TimeStamp}

object BlockCache {

  @SuppressWarnings(Array("org.wartremover.warts.IterableOps"))
  private def computeAverageBlockTime(blockTimes: Seq[TimeStamp]): Duration = {
    if (blockTimes.sizeIs > 1) {
      val (_, diffs) =
        blockTimes.drop(1).foldLeft((blockTimes.head, Seq.empty: Seq[Duration])) {
          case ((prev, acc), ts) =>
            (ts, acc :+ ts.deltaUnsafe(prev))
        }
      diffs.fold(Duration.zero)(_ + _).divUnsafe(diffs.size.toLong)
    } else {
      Duration.zero
    }
  }

  // scalastyle:off
  def apply()(implicit groupSetting: GroupSetting,
              ec: ExecutionContext,
              dc: DatabaseConfig[PostgresProfile]): BlockCache = {
    val groupConfig: GroupConfig = groupSetting.groupConfig

    @SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
    val latestBlockAsyncLoader: AsyncCacheLoader[ChainIndex, LatestBlock] = {
      case (key, _) =>
        run(
          BlockQueries.getLatestBlock(GroupIndex.unsafe(key.from.value),
                                      GroupIndex.unsafe(key.to.value))
        ).map(_.get).asJava.toCompletableFuture
    }

    val cachedLatestBlocks: CaffeineAsyncCache[ChainIndex, LatestBlock] =
      CaffeineAsyncCache {
        Caffeine
          .newBuilder()
          .maximumSize(groupConfig.chainNum.toLong)
          .expireAfterWrite(5, java.util.concurrent.TimeUnit.SECONDS)
          .buildAsync[ChainIndex, LatestBlock](latestBlockAsyncLoader)
      }

    val blockTimeAsyncLoader: AsyncCacheLoader[ChainIndex, Duration] = {
      case (key, _) =>
        val chainFrom = GroupIndex.fromProtocol(key.from)
        val chainTo   = GroupIndex.fromProtocol(key.to)
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
          .expireAfterWrite(5, java.util.concurrent.TimeUnit.SECONDS)
          .buildAsync[ChainIndex, Duration](blockTimeAsyncLoader)
      }

    val cacheRowCount: AsyncReloadingCache[Int] = AsyncReloadingCache(0, 10.seconds) { _ =>
      run(BlockQueries.mainChainQuery.length.result)
    }

    new BlockCache(
      blockTimes   = cachedBlockTimes,
      rowCount     = cacheRowCount,
      latestBlocks = cachedLatestBlocks
    )
  }

}

/** Cache used by Block queries.
  *
  * Encapsulate so the cache mutation is not directly accessible by clients.
  * */
class BlockCache(blockTimes: CaffeineAsyncCache[ChainIndex, Duration],
                 rowCount: AsyncReloadingCache[Int],
                 latestBlocks: CaffeineAsyncCache[ChainIndex, LatestBlock]) {

  /** Operations on `blockTimes` cache */
  def getAllBlockTimes(chainIndexes: Iterable[ChainIndex])(
      implicit ec: ExecutionContext): Future[Seq[(ChainIndex, Duration)]] =
    blockTimes.getAll(chainIndexes)

  /** Operations on `latestBlocks` cache */
  def getAllLatestBlocks()(implicit ec: ExecutionContext,
                           groupSetting: GroupSetting): Future[Seq[(ChainIndex, LatestBlock)]] =
    latestBlocks.getAll(groupSetting.chainIndexes)

  def putLatestBlock(chainIndex: ChainIndex, block: LatestBlock): Unit =
    latestBlocks.put(chainIndex, block)

  def getMainChainBlockCount(): Int =
    rowCount.get()
}
