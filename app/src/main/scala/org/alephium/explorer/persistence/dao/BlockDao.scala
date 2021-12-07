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

import com.typesafe.scalalogging.StrictLogging
import slick.basic.DatabaseConfig
import slick.dbio.DBIOAction
import slick.jdbc.JdbcProfile

import org.alephium.explorer.AnyOps
import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence._
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.persistence.queries.TransactionQueries
import org.alephium.explorer.persistence.schema._
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
  def listIncludingForks(from: TimeStamp, to: TimeStamp): Future[Seq[BlockEntry.Lite]]
  def maxHeight(fromGroup: GroupIndex, toGroup: GroupIndex): Future[Option[Height]]
  def updateMainChain(hash: BlockEntry.Hash,
                      chainFrom: GroupIndex,
                      chainTo: GroupIndex,
                      groupNum: Int): Future[Option[BlockEntry.Hash]]
  def updateMainChainStatus(hash: BlockEntry.Hash, isMainChain: Boolean): Future[Unit]
}

object BlockDao {
  def apply(config: DatabaseConfig[JdbcProfile])(
      implicit executionContext: ExecutionContext): BlockDao =
    new Impl(config)

  class Impl(val config: DatabaseConfig[JdbcProfile])(
      implicit val executionContext: ExecutionContext)
      extends BlockDao
      with BlockHeaderSchema
      with BlockDepsSchema
      with TransactionQueries
      with DBRunner
      with StrictLogging {
    import config.profile.api._

    private val blockDepsQuery = Compiled { blockHash: Rep[BlockEntry.Hash] =>
      blockDepsTable.filter(_.hash === blockHash).sortBy(_.order).map(_.dep)
    }

    private def buildBlockEntryAction(blockHeader: BlockHeader): DBActionR[BlockEntry] =
      for {
        deps <- blockDepsQuery(blockHeader.hash).result
        txs  <- getTransactionsByBlockHash(blockHeader.hash)
      } yield blockHeader.toApi(deps, txs)

    private def buildLiteBlockEntryAction(blockHeader: BlockHeader): DBActionR[BlockEntry.Lite] =
      for {
        number <- countBlockHashTransactions(blockHeader.hash)
      } yield blockHeader.toLiteApi(number)

    private def getBlockEntryLiteAction(hash: BlockEntry.Hash): DBActionR[Option[BlockEntry.Lite]] =
      for {
        headers <- blockHeadersTable.filter(_.hash === hash).result
        blockOpt <- headers.headOption match {
          case None         => DBIOAction.successful(None)
          case Some(header) => buildLiteBlockEntryAction(header).map(Option.apply)
        }
      } yield blockOpt

    private def getBlockEntryAction(hash: BlockEntry.Hash): DBActionR[Option[BlockEntry]] =
      for {
        headers <- blockHeadersTable.filter(_.hash === hash).result
        blocks  <- DBIOAction.sequence(headers.map(buildBlockEntryAction))
      } yield blocks.headOption

    def getLite(hash: BlockEntry.Hash): Future[Option[BlockEntry.Lite]] =
      run(getBlockEntryLiteAction(hash))

    def getTransactions(hash: BlockEntry.Hash, pagination: Pagination): Future[Seq[Transaction]] =
      run(getTransactionsByBlockHashWithPagination(hash, pagination))

    def get(hash: BlockEntry.Hash): Future[Option[BlockEntry]] =
      run(getBlockEntryAction(hash))

    private def getAtHeightAction(fromGroup: GroupIndex,
                                  toGroup: GroupIndex,
                                  height: Height): DBActionR[Seq[BlockEntry]] =
      for {
        headers <- blockHeadersTable
          .filter(header =>
            header.height === height && header.chainFrom === fromGroup && header.chainTo === toGroup)
          .result
        blocks <- DBIOAction.sequence(headers.map(buildBlockEntryAction))
      } yield blocks

    def getAtHeight(fromGroup: GroupIndex,
                    toGroup: GroupIndex,
                    height: Height): Future[Seq[BlockEntry]] =
      run(getAtHeightAction(fromGroup, toGroup, height))

    def insertAction(block: BlockEntity): DBActionRWT[Unit] =
      (for {
        _ <- DBIOAction.sequence(block.deps.zipWithIndex.map {
          case (dep, i) => blockDepsTable.insertOrUpdate((block.hash, dep, i))
        })
        _ <- insertTransactionFromBlockQuery(block)
        _ <- blockHeadersTable.insertOrUpdate(BlockHeader.fromEntity(block)).filter(_ > 0)
      } yield ()).transactionally

    def insert(block: BlockEntity): Future[Unit] = {
      run(insertAction(block))
    }

    def insertAll(blocks: Seq[BlockEntity]): Future[Unit] = {
      run(DBIOAction.sequence(blocks.map(insertAction))).map(_ => ())
    }

    def listMainChainHeaders(mainChain: Query[BlockHeaders, BlockHeader, Seq],
                             pagination: Pagination): DBActionR[Seq[BlockHeader]] = {
      val sorted = if (pagination.reverse) {
        mainChain
          .sortBy(b => (b.timestamp, b.hash.desc))
      } else {
        mainChain
          .sortBy(b => (b.timestamp.desc, b.hash))
      }

      sorted
        .drop(pagination.offset * pagination.limit)
        .take(pagination.limit)
        .result
    }

    def listMainChain(pagination: Pagination): Future[(Seq[BlockEntry.Lite], Int)] = {
      val mainChain = blockHeadersTable.filter(_.mainChain)
      val action =
        for {
          headers <- listMainChainHeaders(mainChain, pagination)
          blocks  <- DBIOAction.sequence(headers.map(buildLiteBlockEntryAction))
          total   <- mainChain.length.result
        } yield (blocks, total)

      run(action)
    }

    def listIncludingForks(from: TimeStamp, to: TimeStamp): Future[Seq[BlockEntry.Lite]] = {
      val action =
        for {
          headers <- blockHeadersTable
            .filter(header => header.timestamp >= from.millis && header.timestamp <= to.millis)
            .sortBy(b => (b.timestamp.desc, b.hash))
            .result
          blocks <- DBIOAction.sequence(headers.map(buildLiteBlockEntryAction))
        } yield blocks

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
      getBlockEntryAction(hash)
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
              block.parent(groupNum).map(Right(_))
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

    private def updateMainChainStatusAction(hash: BlockEntry.Hash,
                                            isMainChain: Boolean): DBActionRWT[Unit] = {
      val query =
        for {
          _ <- transactionsTable
            .filter(_.blockHash === hash)
            .map(_.mainChain)
            .update(isMainChain)
          _ <- outputsTable
            .filter(_.blockHash === hash)
            .map(_.mainChain)
            .update(isMainChain)
          _ <- inputsTable
            .filter(_.blockHash === hash)
            .map(_.mainChain)
            .update(isMainChain)
          _ <- blockHeadersTable
            .filter(_.hash === hash)
            .map(_.mainChain)
            .update(isMainChain)
        } yield ()

      query.transactionally
    }

    def updateMainChainStatus(hash: BlockEntry.Hash, isMainChain: Boolean): Future[Unit] = {
      run(updateMainChainStatusAction(hash, isMainChain))
    }
  }
}
