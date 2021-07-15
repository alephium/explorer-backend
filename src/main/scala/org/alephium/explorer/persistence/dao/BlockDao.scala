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

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.Duration

import com.typesafe.scalalogging.StrictLogging
import slick.basic.DatabaseConfig
import slick.dbio.DBIOAction
import slick.jdbc.JdbcProfile
import slick.jdbc.meta.MTable

import org.alephium.explorer.{sideEffect, AnyOps}
import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence._
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.persistence.queries.TransactionQueries
import org.alephium.explorer.persistence.schema._

trait BlockDao {
  def get(hash: BlockEntry.Hash): Future[Option[BlockEntry]]
  def getLite(hash: BlockEntry.Hash): Future[Option[BlockEntry.Lite]]
  def getTransactions(hash: BlockEntry.Hash): Future[Seq[Transaction]]
  def getAtHeight(fromGroup: GroupIndex,
                  toGroup: GroupIndex,
                  height: Height): Future[Seq[BlockEntry]]
  def insert(block: BlockEntity): Future[Unit]
  def insertAll(blocks: Seq[BlockEntity]): Future[Unit]
  def listMainChain(pagination: Pagination): Future[Seq[BlockEntry.Lite]]
  def listIncludingForks(timeInterval: TimeInterval): Future[Seq[BlockEntry.Lite]]
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
        blocks  <- DBIOAction.sequence(headers.map(buildLiteBlockEntryAction))
      } yield blocks.headOption

    private def getBlockEntryAction(hash: BlockEntry.Hash): DBActionR[Option[BlockEntry]] =
      for {
        headers <- blockHeadersTable.filter(_.hash === hash).result
        blocks  <- DBIOAction.sequence(headers.map(buildBlockEntryAction))
      } yield blocks.headOption

    def getLite(hash: BlockEntry.Hash): Future[Option[BlockEntry.Lite]] =
      run(getBlockEntryLiteAction(hash))

    def getTransactions(hash: BlockEntry.Hash): Future[Seq[Transaction]] =
      run(getTransactionsByBlockHash(hash))

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
        _ <- blockHeadersTable.insertOrUpdate(BlockHeader.fromEntity(block)).filter(_ > 0)
        _ <- DBIOAction.sequence(block.deps.zipWithIndex.map {
          case (dep, i) => blockDepsTable += ((block.hash, dep, i))
        })
        _ <- insertTransactionFromBlockQuery(block)
      } yield ()).transactionally

    def insert(block: BlockEntity): Future[Unit] = {
      run(insertAction(block))
    }

    def insertAll(blocks: Seq[BlockEntity]): Future[Unit] = {
      run(DBIOAction.sequence(blocks.map(insertAction))).map(_ => ())
    }

    def listMainChain(pagination: Pagination): Future[Seq[BlockEntry.Lite]] = {
      val action =
        for {
          headers <- blockHeadersTable
            .filter(_.mainChain)
            .sortBy(b => (b.timestamp.desc, b.hash))
            .drop(pagination.offset * pagination.limit)
            .take(pagination.limit)
            .result
          blocks <- DBIOAction.sequence(headers.map(buildLiteBlockEntryAction))
        } yield blocks

      run(action)
    }

    def listIncludingForks(timeInterval: TimeInterval): Future[Seq[BlockEntry.Lite]] = {
      val action =
        for {
          headers <- blockHeadersTable
            .filter(header =>
              header.timestamp >= timeInterval.from.millis && header.timestamp <= timeInterval.to.millis)
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
          _ <- blockHeadersTable
            .filter(_.hash === hash)
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
        } yield ()

      query.transactionally
    }

    def updateMainChainStatus(hash: BlockEntry.Hash, isMainChain: Boolean): Future[Unit] = {
      run(updateMainChainStatusAction(hash, isMainChain))
    }

    //TODO Look for something like https://flywaydb.org/ to manage schemas
    @SuppressWarnings(
      Array("org.wartremover.warts.JavaSerializable",
            "org.wartremover.warts.Product",
            "org.wartremover.warts.Serializable"))
    private val myTables =
      Seq(blockHeadersTable, blockDepsTable, transactionsTable, inputsTable, outputsTable)
    private val existing = run(MTable.getTables)
    private val f = existing.flatMap { tables =>
      Future.sequence(myTables.map { myTable =>
        val createIfNotExist =
          if (!tables.exists(_.name.name === myTable.baseTableRow.tableName)) {
            myTable.schema.create
          } else {
            DBIOAction.successful(())
          }
        run(createIfNotExist)
      })
    }
    sideEffect {
      Await.result(f, Duration.Inf)
    }
  }
}
