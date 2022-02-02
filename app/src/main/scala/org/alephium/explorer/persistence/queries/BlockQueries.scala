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

package org.alephium.explorer.persistence.queries

import com.typesafe.scalalogging.StrictLogging
import slick.basic.DatabaseConfig
import slick.dbio.DBIOAction
import slick.jdbc.JdbcProfile

import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence._
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.persistence.schema._

trait BlockQueries
    extends BlockHeaderSchema
    with BlockDepsSchema
    with TransactionQueries
    with StrictLogging {

  val config: DatabaseConfig[JdbcProfile]
  import config.profile.api._

  private val blockDepsQuery = Compiled { blockHash: Rep[BlockEntry.Hash] =>
    blockDepsTable.filter(_.hash === blockHash).sortBy(_.order).map(_.dep)
  }

  def buildBlockEntryAction(blockHeader: BlockHeader): DBActionR[BlockEntry] =
    for {
      deps <- blockDepsQuery(blockHeader.hash).result
      txs  <- getTransactionsByBlockHash(blockHeader.hash)
    } yield blockHeader.toApi(deps, txs)

  def buildLiteBlockEntryAction(blockHeader: BlockHeader): DBActionR[BlockEntry.Lite] =
    for {
      number <- countBlockHashTransactions(blockHeader.hash)
    } yield blockHeader.toLiteApi(number)

  def getBlockEntryLiteAction(hash: BlockEntry.Hash): DBActionR[Option[BlockEntry.Lite]] =
    for {
      headers <- blockHeadersTable.filter(_.hash === hash).result
      blockOpt <- headers.headOption match {
        case None         => DBIOAction.successful(None)
        case Some(header) => buildLiteBlockEntryAction(header).map(Option.apply)
      }
    } yield blockOpt

  def getBlockEntryAction(hash: BlockEntry.Hash): DBActionR[Option[BlockEntry]] =
    for {
      headers <- blockHeadersTable.filter(_.hash === hash).result
      blocks  <- DBIOAction.sequence(headers.map(buildBlockEntryAction))
    } yield blocks.headOption

  def getAtHeightAction(fromGroup: GroupIndex,
                        toGroup: GroupIndex,
                        height: Height): DBActionR[Seq[BlockEntry]] =
    for {
      headers <- blockHeadersTable
        .filter(header =>
          header.height === height && header.chainFrom === fromGroup && header.chainTo === toGroup)
        .result
      blocks <- DBIOAction.sequence(headers.map(buildBlockEntryAction))
    } yield blocks

  def insertAction(block: BlockEntity): DBActionRWT[Unit] =
    (for {
      _ <- DBIOAction.sequence(block.deps.zipWithIndex.map {
        case (dep, i) => blockDepsTable.insertOrUpdate((block.hash, dep, i))
      })
      _ <- insertTransactionFromBlockQuery(block)
      _ <- blockHeadersTable.insertOrUpdate(BlockHeader.fromEntity(block)).filter(_ > 0)
    } yield ()).transactionally

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

  /**
    * Order by query for [[blockHeadersTable]]
    *
    * @param prefix If non-empty adds the prefix with dot to all columns.
    */
  private def orderBySQLString(prefix: String, pagination: Pagination): String = {
    val queryPrefix =
      if (prefix.isEmpty) {
        ""
      } else {
        prefix + "."
      }

    if (pagination.reverse) {
      s"order by ${queryPrefix}timestamp, ${queryPrefix}hash desc"
    } else {
      s"order by ${queryPrefix}timestamp desc, ${queryPrefix}hash"
    }
  }

  /**
    * Fetches all main_chain [[blockHeadersTable]] rows and number of transactions in
    * [[transactionsTable]] for the blocks.
    */
  def listMainChainHeadersWithTxnNumberSQL(
      pagination: Pagination): DBActionRWT[Vector[BlockEntry.Lite]] = {
    val block_headers = blockHeadersTable.baseTableRow.tableName
    val transactions  = transactionsTable.baseTableRow.tableName

    sql"""
           |select blocks.*,
           |       count(#$transactions.hash) as tx_number
           |from (select hash,
           |             timestamp,
           |             chain_from,
           |             chain_to,
           |             height,
           |             hashrate
           |      from #$block_headers
           |      where main_chain = true
           |      #${orderBySQLString("", pagination)}
           |      limit ${pagination.limit} offset ${pagination.limit * pagination.offset}) as blocks
           |         left outer join #$transactions on #$transactions.block_hash = blocks.hash
           |group by blocks.hash,
           |         blocks.timestamp,
           |         blocks.chain_from,
           |         blocks.chain_to,
           |         blocks.height,
           |         blocks.hashrate
           |#${orderBySQLString("blocks", pagination)}
           |
           |""".stripMargin
      .as[BlockEntry.Lite](mainChainBlockEntryListGetResult)
  }

  /** Counts main_chain Blocks */
  def countMainChain(): Rep[Int] =
    blockHeadersTable.filter(_.mainChain).length

  def updateMainChainStatusAction(hash: BlockEntry.Hash,
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

  def buildBlockEntryWithoutTxsAction(blockHeader: BlockHeader): DBActionR[BlockEntry] =
    for {
      deps <- blockDepsQuery(blockHeader.hash).result
    } yield blockHeader.toApi(deps, Seq.empty)

  def getBlockEntryWithoutTxsAction(hash: BlockEntry.Hash): DBActionR[Option[BlockEntry]] =
    for {
      headers <- blockHeadersTable.filter(_.hash === hash).result
      blocks  <- DBIOAction.sequence(headers.map(buildBlockEntryWithoutTxsAction))
    } yield blocks.headOption
}
