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

import scala.collection.mutable.ListBuffer

import com.typesafe.scalalogging.StrictLogging
import slick.basic.DatabaseConfig
import slick.dbio.DBIOAction
import slick.jdbc.{JdbcProfile, PositionedParameters, SetParameter, SQLActionBuilder}

import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence._
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.persistence.queries.BlockDepQueries.insertBlockDeps
import org.alephium.explorer.persistence.queries.InputQueries.insertInputs
import org.alephium.explorer.persistence.queries.OutputQueries.insertOutputs
import org.alephium.explorer.persistence.schema._
import org.alephium.explorer.persistence.schema.CustomSetParameter._

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
        case (dep, i) => blockDepsTable.insertOrUpdate(BlockDepEntity(block.hash, dep, i))
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

  /** Inserts block_headers or ignore them if there is a primary key conflict */
  // scalastyle:off magic.number
  def insertBlockHeaders(blocks: Iterable[BlockHeader]): DBActionW[Int] =
    if (blocks.isEmpty) {
      DBIOAction.successful(0)
    } else {
      val placeholder = paramPlaceholder(rows = blocks.size, columns = 14)

      val query =
        s"""
           |insert into $block_headers ("hash",
           |                            "timestamp",
           |                            "chain_from",
           |                            "chain_to",
           |                            "height",
           |                            "main_chain",
           |                            "nonce",
           |                            "version",
           |                            "dep_state_hash",
           |                            "txs_hash",
           |                            "txs_count",
           |                            "target",
           |                            "hashrate",
           |                            "parent")
           |values $placeholder
           |ON CONFLICT ON CONSTRAINT block_headers_pkey
           |    DO NOTHING
           |""".stripMargin

      val parameters: SetParameter[Unit] =
        (_: Unit, params: PositionedParameters) =>
          blocks foreach { block =>
            params >> block.hash
            params >> block.timestamp
            params >> block.chainFrom
            params >> block.chainTo
            params >> block.height
            params >> block.mainChain
            params >> block.nonce
            params >> block.version
            params >> block.depStateHash
            params >> block.txsHash
            params >> block.txsCount
            params >> block.target
            params >> block.hashrate
            params >> block.parent
        }

      SQLActionBuilder(
        queryParts = query,
        unitPConv  = parameters
      ).asUpdate
    }
  // scalastyle:on magic.number

  /** Transactionally write blocks */
  @SuppressWarnings(
    Array("org.wartremover.warts.MutableDataStructures", "org.wartremover.warts.NonUnitStatements"))
  def insertBlockEntity(blocks: Iterable[BlockEntity], groupNum: Int): DBActionRWT[Int] = {
    val blockDeps    = ListBuffer.empty[BlockDepEntity]
    val transactions = ListBuffer.empty[TransactionEntity]
    val inputs       = ListBuffer.empty[InputEntity]
    val outputs      = ListBuffer.empty[OutputEntity]
    val blockHeaders = ListBuffer.empty[BlockHeader]

    //build data for all insert queries in single iteration
    blocks foreach { block =>
      if (block.height.value != 0) blockDeps addAll block.toBlockDepEntities()
      transactions addAll block.transactions
      inputs addAll block.inputs
      outputs addAll block.outputs
      blockHeaders addOne block.toBlockHeader(groupNum)
    }

    val query =
      insertBlockDeps(blockDeps) andThen
        insertTransactions(transactions) andThen
        insertInputs(inputs) andThen
        insertOutputs(outputs) andThen
        insertBlockHeaders(blockHeaders)

    query.transactionally
  }
}
