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
import scala.jdk.CollectionConverters._
import scala.jdk.FutureConverters._

import com.github.benmanes.caffeine.cache._
import com.typesafe.scalalogging.StrictLogging
import slick.basic.DatabaseConfig
import slick.dbio.DBIOAction
import slick.jdbc.JdbcProfile

import org.alephium.explorer.AnyOps
import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence._
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.persistence.queries._
import org.alephium.explorer.persistence.schema._
import org.alephium.protocol.config.GroupConfig
import org.alephium.protocol.model.ChainIndex
import org.alephium.util.TimeStamp

trait BlockDao {
  def get(hash: BlockEntry.Hash): Future[Option[BlockEntry]]
  def getLite(hash: BlockEntry.Hash): Future[Option[BlockEntry.Lite]]
  def getTransactions(hash: BlockEntry.Hash, pagination: Pagination): Future[Seq[Transaction]]
  def getAtHeight(fromGroup: GroupIndex,
                  toGroup: GroupIndex,
                  height: Height): Future[Seq[BlockEntry]]
  def insert(block: BlockEntity): Future[Unit]
  def insertAll(blocks: Seq[BlockEntity]): Future[Unit]
  def listMainChain(pagination: Pagination): Future[(Seq[BlockEntry.Lite], Int)]
  def listMainChainSQL(pagination: Pagination): Future[(Seq[BlockEntry.Lite], Int)]
  def listIncludingForks(from: TimeStamp, to: TimeStamp): Future[Seq[BlockEntry.Lite]]
  def maxHeight(fromGroup: GroupIndex, toGroup: GroupIndex): Future[Option[Height]]
  def updateMainChain(hash: BlockEntry.Hash,
                      chainFrom: GroupIndex,
                      chainTo: GroupIndex,
                      groupNum: Int): Future[Option[BlockEntry.Hash]]
  def updateMainChainStatus(hash: BlockEntry.Hash, isMainChain: Boolean): Future[Unit]
  def latestBlocks(): Future[Seq[(ChainIndex, LatestBlock)]]
  def updateLatestBlock(block: BlockEntity): Future[Unit]
  def getAddressForInput(input:InputEntity): Future[Option[Address]]
}

object BlockDao {
  def apply(groupNum: Int, config: DatabaseConfig[JdbcProfile])(
      implicit executionContext: ExecutionContext): BlockDao =
    new Impl(groupNum, config)
  @SuppressWarnings(Array("org.wartremover.warts.MutableDataStructures"))
  class Impl(groupNum: Int, val config: DatabaseConfig[JdbcProfile])(
      implicit val executionContext: ExecutionContext)
      extends BlockDao
      with CustomTypes
      with BlockHeaderSchema
      with BlockDepsSchema
      with BlockQueries
      with LatestBlockSchema
      with TransactionQueries
      with DBRunner
      with StrictLogging {
    import config.profile.api._

    private implicit val groupConfig: GroupConfig = new GroupConfig { val groups = groupNum }

    private val chainIndexes: java.lang.Iterable[ChainIndex] = (for {
      i <- 0 to groupNum - 1
      j <- 0 to groupNum - 1
    } yield (ChainIndex.unsafe(i, j))).asJava

    @SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
    private val asyncLoader: AsyncCacheLoader[ChainIndex, LatestBlock] = {
      case (key, _) =>
        run(
          getLatestBlock(GroupIndex.unsafe(key.from.value), GroupIndex.unsafe(key.to.value))
        ).map(_.get).asJava.toCompletableFuture
    }

    private val cachedLatestBlocks: AsyncLoadingCache[ChainIndex, LatestBlock] = Caffeine
      .newBuilder()
      .maximumSize(groupConfig.chainNum.toLong)
      .expireAfterWrite(5, java.util.concurrent.TimeUnit.SECONDS)
      .buildAsync(asyncLoader)

    def getLite(hash: BlockEntry.Hash): Future[Option[BlockEntry.Lite]] =
      run(getBlockEntryLiteAction(hash))

    def getTransactions(hash: BlockEntry.Hash, pagination: Pagination): Future[Seq[Transaction]] =
      run(getTransactionsByBlockHashWithPagination(hash, pagination))

    def get(hash: BlockEntry.Hash): Future[Option[BlockEntry]] =
      run(getBlockEntryAction(hash))

    def getAtHeight(fromGroup: GroupIndex,
                    toGroup: GroupIndex,
                    height: Height): Future[Seq[BlockEntry]] =
      run(getAtHeightAction(fromGroup, toGroup, height))

    def insert(block: BlockEntity): Future[Unit] = {
      run(insertAction(block, groupNum))
    }

    def insertAll(blocks: Seq[BlockEntity]): Future[Unit] = {
      run(DBIOAction.sequence(blocks.map(b => insertAction(b, groupNum)))).map(_ => ())
    }

    def listMainChain(pagination: Pagination): Future[(Seq[BlockEntry.Lite], Int)] = {
      val mainChain = blockHeadersTable.filter(_.mainChain)
      val action =
        for {
          headers <- listMainChainHeaders(mainChain, pagination)
          total   <- mainChain.length.result
        } yield (headers.map(_.toLiteApi), total)

      run(action)
    }

    /** SQL version of [[listMainChain]] */
    def listMainChainSQL(pagination: Pagination): Future[(Seq[BlockEntry.Lite], Int)] = {
      val blockEntries = run(listMainChainHeadersWithTxnNumberSQL(pagination))
      val count        = run(countMainChain().result)
      blockEntries.zip(count)
    }

    def listIncludingForks(from: TimeStamp, to: TimeStamp): Future[Seq[BlockEntry.Lite]] = {
      val action =
        for {
          headers <- blockHeadersTable
            .filter(header => header.timestamp >= from && header.timestamp <= to)
            .sortBy(b => (b.timestamp.desc, b.hash))
            .result
        } yield headers.map(_.toLiteApi)

      run(action)
    }

    def maxHeight(fromGroup: GroupIndex, toGroup: GroupIndex): Future[Option[Height]] = {
      val query =
        blockHeadersTable
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

    def latestBlocks(): Future[Seq[(ChainIndex, LatestBlock)]] = {
      cachedLatestBlocks
        .getAll(chainIndexes)
        .asScala
        .map(_.asScala.toSeq)
    }

    def updateLatestBlock(block: BlockEntity): Future[Unit] = {
      val chainIndex  = ChainIndex.unsafe(block.chainFrom.value, block.chainTo.value)
      val latestBlock = LatestBlock.fromEntity(block)
      run(latestBlocksTable.insertOrUpdate(latestBlock)).map { _ =>
        cachedLatestBlocks.put(
          chainIndex,
          Future.successful(latestBlock).asJava.toCompletableFuture
        )
      }
    }

  def getAddressForInput(input:InputEntity): Future[Option[Address]] = {
    run(
    outputsTable.filter{out =>
      out.key === input.outputRefKey
    }.map(_.address).result.headOption).flatMap {
      case None =>
        println("Cannot find output")
        get(input.blockHash).map { block =>
          println(s"${Console.RED}${Console.BOLD}*** input ***\n\t${Console.RESET}${input}")
          println(s"${Console.RED}${Console.BOLD}*** block ***\n\t${Console.RESET}${block}")
          None
        }

     case Some(address) => Future.successful(Some(address))
    }
  }
  }
}
