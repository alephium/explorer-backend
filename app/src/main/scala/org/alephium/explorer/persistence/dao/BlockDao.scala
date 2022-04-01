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

package org.alephium.explorer.persistence.dao

import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.FutureConverters._

import com.github.benmanes.caffeine.cache._
import com.typesafe.scalalogging.StrictLogging
import slick.basic.DatabaseConfig
import slick.dbio.DBIOAction
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.AnyOps
import org.alephium.explorer.api.model._
import org.alephium.explorer.cache.CaffeineAsyncCache
import org.alephium.explorer.persistence._
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.persistence.queries._
import org.alephium.explorer.persistence.queries.InputQueries._
import org.alephium.explorer.persistence.schema._
import org.alephium.protocol.config.GroupConfig
import org.alephium.protocol.model.ChainIndex
import org.alephium.util.{Duration, TimeStamp}

trait BlockDao {
  def get(hash: BlockEntry.Hash): Future[Option[BlockEntry]]
  def getLite(hash: BlockEntry.Hash): Future[Option[BlockEntryLite]]
  def getTransactions(hash: BlockEntry.Hash, pagination: Pagination): Future[Seq[Transaction]]
  def getAtHeight(fromGroup: GroupIndex,
                  toGroup: GroupIndex,
                  height: Height): Future[Seq[BlockEntry]]
  def insert(block: BlockEntity): Future[Unit]
  def insertAll(blocks: Seq[BlockEntity]): Future[Unit]
  def listMainChain(pagination: Pagination): Future[(Seq[BlockEntryLite], Int)]
  def listMainChainSQL(pagination: Pagination): Future[(Seq[BlockEntryLite], Int)]
  def listMainChainSQLCached(pagination: Pagination): Future[(Seq[BlockEntryLite], Int)]
  def listIncludingForks(from: TimeStamp, to: TimeStamp): Future[Seq[BlockEntryLite]]
  def maxHeight(fromGroup: GroupIndex, toGroup: GroupIndex): Future[Option[Height]]
  def updateTransactionPerAddress(block: BlockEntity): Future[Seq[InputEntity]]
  def updateMainChain(hash: BlockEntry.Hash,
                      chainFrom: GroupIndex,
                      chainTo: GroupIndex,
                      groupNum: Int): Future[Option[BlockEntry.Hash]]
  def updateMainChainStatus(hash: BlockEntry.Hash, isMainChain: Boolean): Future[Unit]
  def latestBlocks(): Future[Seq[(ChainIndex, LatestBlock)]]
  def updateLatestBlock(block: BlockEntity): Future[Unit]
  def updateInputs(inputs: Seq[InputEntity]): Future[Int]
  def getAverageBlockTime(): Future[Seq[(ChainIndex, Duration)]]
}

object BlockDao {
  def apply(groupNum: Int, databaseConfig: DatabaseConfig[PostgresProfile])(
      implicit executionContext: ExecutionContext): BlockDao =
    new Impl(groupNum, databaseConfig)
  @SuppressWarnings(Array("org.wartremover.warts.MutableDataStructures"))
  class Impl(groupNum: Int, val databaseConfig: DatabaseConfig[PostgresProfile])(
      implicit val executionContext: ExecutionContext)
      extends BlockDao
      with CustomTypes
      with BlockQueries
      with TransactionQueries
      with DBRunner
      with StrictLogging {

    private implicit val groupConfig: GroupConfig = new GroupConfig { val groups = groupNum }

    private val chainIndexes: IndexedSeq[ChainIndex] = for {
      i <- 0 until groupNum
      j <- 0 until groupNum
    } yield ChainIndex.unsafe(i, j)

    @SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
    private val latestBlockAsyncLoader: AsyncCacheLoader[ChainIndex, LatestBlock] = {
      case (key, _) =>
        run(
          getLatestBlock(GroupIndex.unsafe(key.from.value), GroupIndex.unsafe(key.to.value))
        ).map(_.get).asJava.toCompletableFuture
    }

    private val cachedLatestBlocks: CaffeineAsyncCache[ChainIndex, LatestBlock] =
      CaffeineAsyncCache {
        Caffeine
          .newBuilder()
          .maximumSize(groupConfig.chainNum.toLong)
          .expireAfterWrite(5, java.util.concurrent.TimeUnit.SECONDS)
          .buildAsync[ChainIndex, LatestBlock](latestBlockAsyncLoader)
      }

    private val blockTimeAsyncLoader: AsyncCacheLoader[ChainIndex, Duration] = {
      case (key, _) =>
        val chainFrom = GroupIndex.fromProtocol(key.from)
        val chainTo   = GroupIndex.fromProtocol(key.to)
        (for {
          latestBlock <- cachedLatestBlocks.get(key)
          after = latestBlock.timestamp.minusUnsafe(Duration.ofHoursUnsafe(2))
          blockTimes <- run(getBlockTimes(chainFrom, chainTo, after))
        } yield {
          computeAverageBlockTime(blockTimes)
        }).asJava.toCompletableFuture
    }

    @SuppressWarnings(Array("org.wartremover.warts.TraversableOps"))
    private def computeAverageBlockTime(blockTimes: Seq[TimeStamp]): Duration = {
      if (blockTimes.size > 1) {
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

    private val cachedBlockTimes: CaffeineAsyncCache[ChainIndex, Duration] =
      CaffeineAsyncCache {
        Caffeine
          .newBuilder()
          .maximumSize(groupConfig.chainNum.toLong)
          .expireAfterWrite(5, java.util.concurrent.TimeUnit.SECONDS)
          .buildAsync[ChainIndex, Duration](blockTimeAsyncLoader)
      }

    private val cacheRowCount: CaffeineAsyncCache[Query[_, _, Seq], Int] =
      CaffeineAsyncCache.rowCountCache(this) {
        Caffeine
          .newBuilder()
          .refreshAfterWrite(2, java.util.concurrent.TimeUnit.MINUTES)
      }

    def getRowCountFromCacheIfPresent[E, U](query: Query[E, U, Seq]): Option[Future[Int]] =
      cacheRowCount.getIfPresent(query)

    def invalidateCacheRowCount(): Unit =
      cacheRowCount.invalidateAll()

    def getLite(hash: BlockEntry.Hash): Future[Option[BlockEntryLite]] =
      run(getBlockEntryLiteAction(hash))

    def getTransactions(hash: BlockEntry.Hash, pagination: Pagination): Future[Seq[Transaction]] =
      run(getTransactionsByBlockHashWithPagination(hash, pagination))

    def get(hash: BlockEntry.Hash): Future[Option[BlockEntry]] =
      run(getBlockEntryAction(hash))

    def getAtHeight(fromGroup: GroupIndex,
                    toGroup: GroupIndex,
                    height: Height): Future[Seq[BlockEntry]] =
      run(getAtHeightAction(fromGroup, toGroup, height))

    def updateTransactionPerAddress(block: BlockEntity): Future[Seq[InputEntity]] = {
      run(
        updateTransactionPerAddressAction(block.outputs, block.inputs)
      )
    }

    /** Inserts a single block transactionally via SQL */
    def insert(block: BlockEntity): Future[Unit] =
      insertAll(Seq(block))

    /** Inserts a multiple blocks transactionally via SQL */
    def insertAll(blocks: Seq[BlockEntity]): Future[Unit] =
      run(insertBlockEntity(blocks, groupNum))
        .map { _ =>
          cacheRowCount.invalidate(mainChainQuery)
        }

    def listMainChain(pagination: Pagination): Future[(Seq[BlockEntryLite], Int)] = {
      val mainChain = BlockHeaderSchema.table.filter(_.mainChain)
      val action =
        for {
          headers <- listMainChainHeaders(mainChain, pagination)
          total   <- mainChain.length.result
        } yield (headers.map(_.toLiteApi), total)

      run(action)
    }

    /** SQL version of [[listMainChain]] */
    def listMainChainSQL(pagination: Pagination): Future[(Seq[BlockEntryLite], Int)] = {
      val blockEntries = run(listMainChainHeadersWithTxnNumberSQL(pagination))
      val count        = run(countMainChain().result)
      blockEntries.zip(count)
    }

    def listMainChainSQLCached(pagination: Pagination): Future[(Seq[BlockEntryLite], Int)] = {
      val blockEntries = run(listMainChainHeadersWithTxnNumberSQL(pagination))
      val count        = cacheRowCount.get(mainChainQuery)
      blockEntries.zip(count)
    }

    def listIncludingForks(from: TimeStamp, to: TimeStamp): Future[Seq[BlockEntryLite]] = {
      val action =
        for {
          headers <- BlockHeaderSchema.table
            .filter(header => header.timestamp >= from && header.timestamp <= to)
            .sortBy(b => (b.timestamp.desc, b.hash))
            .result
        } yield headers.map(_.toLiteApi)

      run(action)
    }

    def maxHeight(fromGroup: GroupIndex, toGroup: GroupIndex): Future[Option[Height]] = {
      val query =
        BlockHeaderSchema.table
          .filter(header => header.chainFrom === fromGroup && header.chainTo === toGroup)
          .map(_.height)
          .max

      run(query.result)
    }

    @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
    private def updateMainChainAction(hash: BlockEntry.Hash,
                                      chainFrom: GroupIndex,
                                      chainTo: GroupIndex,
                                      groupNum: Int): DBActionRWT[Option[BlockEntry.Hash]] = {
      getBlockHeaderAction(hash)
        .flatMap {
          case Some(block) if !block.mainChain =>
            assert(block.chainFrom == chainFrom && block.chainTo == chainTo)
            (for {
              blocks <- getAtHeightAction(block.chainFrom, block.chainTo, block.height)
              _ <- DBIOAction.sequence(
                blocks
                  .map(_.hash)
                  .filterNot(_ === block.hash)
                  .map(updateMainChainStatusAction(_, false)))
              _ <- updateMainChainStatusAction(hash, true)
            } yield {
              block.parent.map(Right(_))
            })
          case None => DBIOAction.successful(Some(Left(hash)))
          case _    => DBIOAction.successful(None)
        }
        .flatMap {
          case Some(Right(parent)) => updateMainChainAction(parent, chainFrom, chainTo, groupNum)
          case Some(Left(missing)) => DBIOAction.successful(Some(missing))
          case None                => DBIOAction.successful(None)
        }
    }

    def updateMainChain(hash: BlockEntry.Hash,
                        chainFrom: GroupIndex,
                        chainTo: GroupIndex,
                        groupNum: Int): Future[Option[BlockEntry.Hash]] = {
      run(updateMainChainAction(hash, chainFrom, chainTo, groupNum))
    }

    def updateMainChainStatus(hash: BlockEntry.Hash, isMainChain: Boolean): Future[Unit] = {
      run(updateMainChainStatusAction(hash, isMainChain))
    }

    def latestBlocks(): Future[Seq[(ChainIndex, LatestBlock)]] =
      cachedLatestBlocks.getAll(chainIndexes)

    def getAverageBlockTime(): Future[Seq[(ChainIndex, Duration)]] =
      cachedBlockTimes.getAll(chainIndexes)

    def updateLatestBlock(block: BlockEntity): Future[Unit] = {
      val chainIndex  = ChainIndex.unsafe(block.chainFrom.value, block.chainTo.value)
      val latestBlock = LatestBlock.fromEntity(block)
      run(LatestBlockSchema.table.insertOrUpdate(latestBlock)).map { _ =>
        cachedLatestBlocks.put(chainIndex, latestBlock)
      }
    }

    def updateInputs(inputs: Seq[InputEntity]): Future[Int] = {
      run(
        DBIOAction.sequence(inputs.map(insertTxPerAddressFromInput))
      ).map(_.sum)
    }
  }
}
