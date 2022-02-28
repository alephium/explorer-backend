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
    with LatestBlockSchema
    with TransactionQueries
    with CustomTypes
    with StrictLogging {

  val block_headers = blockHeadersTable.baseTableRow.tableName //block_headers table name

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

  def getBlockEntryLiteAction(hash: BlockEntry.Hash): DBActionR[Option[BlockEntry.Lite]] =
    for {
      header <- blockHeadersTable.filter(_.hash === hash).result.headOption
    } yield header.map(_.toLiteApi)

  def getBlockEntryAction(hash: BlockEntry.Hash): DBActionR[Option[BlockEntry]] =
    for {
      headers <- blockHeadersTable.filter(_.hash === hash).result
      blocks  <- DBIOAction.sequence(headers.map(buildBlockEntryAction))
    } yield blocks.headOption

  def getBlockHeaderAction(hash: BlockEntry.Hash): DBActionR[Option[BlockHeader]] = {
    sql"""
       |SELECT *
       |FROM #$block_headers
       |WHERE hash = '\x#${hash.toString}'
       |""".stripMargin
      .as[BlockHeader](blockHeaderGetResult)
      .headOption
  }

  def getBlockHashesAtHeight(fromGroup: GroupIndex,
                             toGroup: GroupIndex,
                             height: Height): DBActionSR[BlockEntry.Hash] = {
    sql"""
       |SELECT hash
       |FROM #$block_headers
       |WHERE chain_from = ${fromGroup.value}
       |AND chain_to = ${toGroup.value}
       |AND height = ${height.value}
       |""".stripMargin
      .as[BlockEntry.Hash]
  }

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

  def insertAction(block: BlockEntity, groupNum: Int): DBActionRWT[Unit] =
    (for {
      _ <- DBIOAction.sequence(block.deps.zipWithIndex.map {
        case (dep, i) => blockDepsTable.insertOrUpdate((block.hash, dep, i))
      })
      _ <- insertTransactionFromBlockQuery(block)
      _ <- blockHeadersTable.insertOrUpdate(BlockHeader.fromEntity(block, groupNum)).filter(_ > 0)
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
  private def orderBySQLString(prefix: String, reverse: Boolean): String = {
    val columnPrefix =
      if (prefix.isEmpty) {
        prefix
      } else {
        prefix + "."
      }

    if (reverse) {
      s"order by ${columnPrefix}timestamp, ${columnPrefix}hash desc"
    } else {
      s"order by ${columnPrefix}timestamp desc, ${columnPrefix}hash"
    }
  }

  /** Reverse order by without prefix */
  private val LIST_BLOCKS_ORDER_BY_REVERSE: String =
    orderBySQLString(prefix = "", reverse = true)

  /** Forward order by without prefix */
  private val LIST_BLOCKS_ORDER_BY_FORWARD: String =
    orderBySQLString(prefix = "", reverse = false)

  /**
    * Fetches all main_chain [[blockHeadersTable]] rows
    */
  def listMainChainHeadersWithTxnNumberSQL(
      pagination: Pagination): DBActionRWT[Vector[BlockEntry.Lite]] = {

    //order by for inner query
    val orderBy =
      if (pagination.reverse) {
        LIST_BLOCKS_ORDER_BY_REVERSE
      } else {
        LIST_BLOCKS_ORDER_BY_FORWARD
      }

    sql"""
           |select hash,
           |       timestamp,
           |       chain_from,
           |       chain_to,
           |       height,
           |       main_chain,
           |       hashrate,
           |       txs_count
           |from #$block_headers
           |where main_chain = true
           |#$orderBy
           |limit ${pagination.limit} offset ${pagination.limit * pagination.offset}
           |""".stripMargin
      .as[BlockEntry.Lite](blockEntryListGetResult)
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

  def updateMainChainStatusActionSQL(hash: BlockEntry.Hash,
                                     isMainChain: Boolean): DBActionRWT[Int] = {
    sqlu"""
            UPDATE transactions
            SET main_chain = $isMainChain
            WHERE block_hash = $hash
          """
      .andThen(
        sqlu"""UPDATE outputs
            SET main_chain = $isMainChain
            WHERE block_hash = $hash
            """
      )
      .andThen(
        sqlu"""UPDATE inputs
            SET main_chain = $isMainChain
            WHERE block_hash = $hash
            """
      )
      .andThen(
        sqlu"""UPDATE block_headers
            SET main_chain = $isMainChain
            WHERE hash = $hash
            """
      )
      .transactionally
  }


  def updateMainChainAtAction(hash: BlockEntry.Hash,
                              chainFrom: GroupIndex, chainTo:GroupIndex, height: Height): DBActionRWT[Int] = {
    sqlu"""
            UPDATE transactions
            SET main_chain = (CASE WHEN block_hash = $hash THEN true ELSE false END)
            WHERE chain_from = ${chainFrom.value}
            AND chain_to = ${chainTo.value}
            AND height = ${height.value}
          """
      .andThen(
        sqlu"""UPDATE outputs
            SET main_chain = (CASE WHEN block_hash = $hash THEN true ELSE false END)
            WHERE chain_from = ${chainFrom.value}
            AND chain_to = ${chainTo.value}
            AND height = ${height.value}
            """
      )
      .andThen(
        sqlu"""UPDATE inputs
            SET main_chain = (CASE WHEN block_hash = $hash THEN true ELSE false END)
            WHERE chain_from = ${chainFrom.value}
            AND chain_to = ${chainTo.value}
            AND height = ${height.value}
            """
      )
      .andThen(
        sqlu"""UPDATE block_headers
            SET main_chain = (CASE WHEN hash = $hash THEN true ELSE false END)
            WHERE chain_from = ${chainFrom.value}
            AND chain_to = ${chainTo.value}
            AND height = ${height.value}
            """
      )
      .transactionally
  }

  def updateMainChainStatusInActionSQL(hashes: Seq[BlockEntry.Hash],
                                       isMainChain: Boolean): DBActionRWT[Int] = {
    if (hashes.nonEmpty) {
      val str = hashes.map(b => s"'\\x${b.toString}'").mkString(",")
      sqlu"""
            UPDATE transactions
            SET main_chain = $isMainChain
            WHERE block_hash IN (#$str)
          """
        .andThen(
          sqlu"""UPDATE outputs
            SET main_chain = $isMainChain
            WHERE block_hash IN (#$str)
            """
        )
        .andThen(
          sqlu"""UPDATE inputs
            SET main_chain = $isMainChain
            WHERE block_hash IN (#$str)
            """
        )
        .andThen(
          sqlu"""UPDATE block_headers
            SET main_chain = $isMainChain
            WHERE hash IN (#$str)
            """
        )
        .transactionally
    } else {
      DBIOAction.successful(0)
    }
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

  def getLatestBlock(chainFrom: GroupIndex, chainTo: GroupIndex): DBActionR[Option[LatestBlock]] = {
    latestBlocksTable
      .filter { block =>
        block.chainFrom === chainFrom && block.chainTo === chainTo
      }
      .result
      .headOption
  }
}
