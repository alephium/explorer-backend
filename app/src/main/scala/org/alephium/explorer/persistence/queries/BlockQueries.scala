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

import scala.collection.immutable.ArraySeq
import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext

import com.typesafe.scalalogging.StrictLogging
import slick.dbio.DBIOAction
import slick.jdbc.{PositionedParameters, SetParameter, SQLActionBuilder}
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.GroupSetting
import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence._
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.persistence.queries.InputQueries.insertInputs
import org.alephium.explorer.persistence.queries.OutputQueries.insertOutputs
import org.alephium.explorer.persistence.queries.TransactionQueries._
import org.alephium.explorer.persistence.schema._
import org.alephium.explorer.persistence.schema.CustomGetResult._
import org.alephium.explorer.persistence.schema.CustomJdbcTypes._
import org.alephium.explorer.persistence.schema.CustomSetParameter._
import org.alephium.explorer.util.SlickExplainUtil._
import org.alephium.explorer.util.SlickUtil._
import org.alephium.protocol.model.{BlockHash, ChainIndex, GroupIndex}
import org.alephium.util.TimeStamp

object BlockQueries extends StrictLogging {

  @SuppressWarnings(Array("org.wartremover.warts.PublicInference"))
  val block_headers = BlockHeaderSchema.table.baseTableRow.tableName // block_headers table name

  @SuppressWarnings(Array("org.wartremover.warts.PublicInference"))
  val mainChainQuery = BlockHeaderSchema.table.filter(_.mainChain)

  def explainMainChainQuery()(implicit ec: ExecutionContext): DBActionR[ExplainResult] =
    mainChainQuery.result.explainAnalyze() map { explain =>
      ExplainResult(
        queryName = "mainChainQuery",
        queryInput = "Unit",
        explain = explain,
        messages = Iterable.empty,
        passed = explain.mkString contains "block_headers_main_chain_idx"
      )
    }

  def buildBlockEntryAction(
      blockHeader: BlockHeader
  )(implicit ec: ExecutionContext): DBActionR[BlockEntry] =
    for {
      deps <- getDepsForBlock(blockHeader.hash)
      txs  <- getTransactionsByBlockHash(blockHeader.hash)
    } yield blockHeader.toApi(deps, txs)

  def getBlockEntryLiteAction(
      hash: BlockHash
  )(implicit ec: ExecutionContext): DBActionR[Option[BlockEntryLite]] =
    for {
      header <- BlockHeaderSchema.table.filter(_.hash === hash).result.headOption
    } yield header.map(_.toLiteApi)

  /** For a given `BlockHash` returns its basic chain information */
  def getBlockChainInfo(hash: BlockHash): DBActionR[Option[(GroupIndex, GroupIndex, Boolean)]] =
    sql"""
         SELECT chain_from,
                chain_to,
                main_chain
         FROM block_headers
         WHERE hash = $hash
         """
      .asAS[(GroupIndex, GroupIndex, Boolean)]
      .headOption

  def getBlockEntryAction(
      hash: BlockHash
  )(implicit ec: ExecutionContext): DBActionR[Option[BlockEntry]] =
    for {
      headers <- BlockHeaderSchema.table.filter(_.hash === hash).result
      blocks  <- DBIOAction.sequence(headers.map(buildBlockEntryAction))
    } yield blocks.headOption

  def getBlockHeaderAction(hash: BlockHash): DBActionR[Option[BlockHeader]] =
    sql"""
         SELECT *
         FROM #$block_headers
         WHERE hash = $hash
         """
      .asASE[BlockHeader](blockHeaderGetResult)
      .headOption

  private def getHeadersAtHeightQuery(
      fromGroup: GroupIndex,
      toGroup: GroupIndex,
      height: Height
  ): DBActionSR[BlockHeader] =
    sql"""
         SELECT *
         FROM #$block_headers
         WHERE chain_from = $fromGroup
         AND chain_to = $toGroup
         AND height = $height
         """
      .asASE[BlockHeader](blockHeaderGetResult)

  /** Fetch bloch-hashes belonging to the input chain-index at a height, ignoring/filtering-out one
    * block-hash.
    *
    * @param fromGroup
    *   `chain_from` of the blocks
    * @param toGroup
    *   `chain_to` of the blocks
    * @param height
    *   `height` of the blocks
    * @param hashToIgnore
    *   the `block-hash` to ignore or filter-out.
    */
  def getHashesAtHeightIgnoringOne(
      fromGroup: GroupIndex,
      toGroup: GroupIndex,
      height: Height,
      hashToIgnore: BlockHash
  ): DBActionSR[BlockHash] =
    sql"""
         SELECT hash
         FROM #$block_headers
         WHERE chain_from = $fromGroup
         AND chain_to = $toGroup
         AND height = $height
         AND hash != $hashToIgnore
         """
      .asASE[BlockHash](blockEntryHashGetResult)

  def getAtHeightAction(fromGroup: GroupIndex, toGroup: GroupIndex, height: Height)(implicit
      ec: ExecutionContext
  ): DBActionR[ArraySeq[BlockEntry]] =
    for {
      headers <- getHeadersAtHeightQuery(fromGroup, toGroup, height)
      blocks  <- DBIOAction.sequence(headers.map(buildBlockEntryAction))
    } yield blocks

  /** Order by query for [[org.alephium.explorer.persistence.schema.BlockHeaderSchema.table]]
    */
  private def orderBy(reverse: Boolean): String =
    if (reverse) {
      s"order by block_timestamp, hash desc"
    } else {
      s"order by block_timestamp desc, hash"
    }

  /** Reverse order by without prefix */
  private val LIST_BLOCKS_ORDER_BY_REVERSE: String =
    orderBy(reverse = true)

  /** Forward order by without prefix */
  private val LIST_BLOCKS_ORDER_BY_FORWARD: String =
    orderBy(reverse = false)

  /** Fetches all main_chain [[org.alephium.explorer.persistence.schema.BlockHeaderSchema.table]]
    * rows
    */
  def listMainChainHeadersWithTxnNumber(
      pagination: Pagination.Reversible
  ): DBActionRWT[ArraySeq[BlockEntryLite]] =
    listMainChainHeadersWithTxnNumberBuilder(pagination)
      .asASE[BlockEntryLite](blockEntryListGetResult)

  def explainListMainChainHeadersWithTxnNumber(
      pagination: Pagination.Reversible
  )(implicit ec: ExecutionContext): DBActionR[ExplainResult] =
    listMainChainHeadersWithTxnNumberBuilder(pagination).explainAnalyze() map { explain =>
      ExplainResult(
        queryName = "listMainChainHeadersWithTxnNumber",
        queryInput = pagination.toString,
        explain = explain,
        messages = Iterable.empty,
        passed = explain.mkString contains "block_headers_full_index"
      )
    }

  def listMainChainHeadersWithTxnNumberBuilder(
      pagination: Pagination.Reversible
  ): SQLActionBuilder = {
    // order by for inner query
    val orderBy =
      if (pagination.reverse) {
        LIST_BLOCKS_ORDER_BY_REVERSE
      } else {
        LIST_BLOCKS_ORDER_BY_FORWARD
      }

    sql"""
         select hash,
                block_timestamp,
                chain_from,
                chain_to,
                height,
                main_chain,
                hashrate,
                txs_count
         from #$block_headers
         where main_chain = true
         #$orderBy
         limit ${pagination.limit} offset ${pagination.offset}
         """
  }

  def updateMainChainStatusQuery(block: BlockHash, mainChain: Boolean): DBActionRWT[Int] =
    updateMainChainStatuses(Array(block), mainChain)

  /** Updates the block and the block's dependant tables with new `mainChain` value.
    *
    * @param blocks
    *   Blocks to update
    * @param mainChain
    *   New mainChain value
    * @return
    *   The row count for SQL Data Manipulation Language (DML) statements or 0 for SQL statements
    *   that return nothing
    */
  def updateMainChainStatuses(blocks: Iterable[BlockHash], mainChain: Boolean): DBActionRWT[Int] =
    if (blocks.isEmpty) {
      DBIOAction.successful(0)
    } else {
      def whereClause(columnName: String): String =
        Array.fill(blocks.size)(s"$columnName = ?").mkString(" OR ")

      val query =
        s"""
           BEGIN;
           UPDATE transactions              SET main_chain = ? WHERE ${whereClause("block_hash")};
           UPDATE outputs                   SET main_chain = ? WHERE ${whereClause("block_hash")};
           UPDATE inputs                    SET main_chain = ? WHERE ${whereClause("block_hash")};
           UPDATE block_headers             SET main_chain = ? WHERE ${whereClause("hash")};
           UPDATE transaction_per_addresses SET main_chain = ? WHERE ${whereClause("block_hash")};
           UPDATE transaction_per_token     SET main_chain = ? WHERE ${whereClause("block_hash")};
           UPDATE token_tx_per_addresses    SET main_chain = ? WHERE ${whereClause("block_hash")};
           UPDATE token_outputs             SET main_chain = ? WHERE ${whereClause("block_hash")};
           COMMIT;
           """

      val parameters: SetParameter[Unit] =
        (_: Unit, params: PositionedParameters) =>
          (1 to 8) foreach { _ =>
            params >> mainChain
            blocks foreach (params >> _)
          }

      SQLActionBuilder(
        sql = query,
        setParameter = parameters
      ).asUpdate
    }

  def getLatestBlock(chainFrom: GroupIndex, chainTo: GroupIndex): DBActionR[Option[LatestBlock]] = {
    LatestBlockSchema.table
      .filter { block =>
        block.chainFrom === chainFrom && block.chainTo === chainTo
      }
      .result
      .headOption
  }

  /** Inserts block_headers or ignore them if there is a primary key conflict */
  // scalastyle:off magic.number
  def insertBlockHeaders(blocks: Iterable[BlockHeader]): DBActionW[Int] =
    QuerySplitter.splitUpdates(rows = blocks, columnsPerRow = 16) { (blocks, placeholder) =>
      val query =
        s"""
           INSERT INTO $block_headers ("hash",
                                       "block_timestamp",
                                       "chain_from",
                                       "chain_to",
                                       "height",
                                       "main_chain",
                                       "nonce",
                                       "block_version",
                                       "dep_state_hash",
                                       "txs_hash",
                                       "txs_count",
                                       "target",
                                       "hashrate",
                                       "parent",
                                       "deps",
                                       "ghost_uncles")
           VALUES $placeholder
           ON CONFLICT ON CONSTRAINT block_headers_pkey
               DO NOTHING
           """

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
            params >> block.deps
            params >> block.ghostUncles
          }

      SQLActionBuilder(
        sql = query,
        setParameter = parameters
      ).asUpdate
    }
  // scalastyle:on magic.number

  /** Transactionally write blocks */
  @SuppressWarnings(
    Array("org.wartremover.warts.MutableDataStructures", "org.wartremover.warts.NonUnitStatements")
  )
  def insertBlockEntity(blocks: Iterable[BlockEntity], groupNum: Int): DBActionRWT[Unit] = {
    val transactions = ListBuffer.empty[TransactionEntity]
    val inputs       = ListBuffer.empty[InputEntity]
    val outputs      = ListBuffer.empty[OutputEntity]
    val blockHeaders = ListBuffer.empty[BlockHeader]

    // build data for all insert queries in single iteration
    blocks foreach { block =>
      transactions addAll block.transactions
      inputs addAll block.inputs
      outputs addAll block.outputs
      blockHeaders addOne block.toBlockHeader(groupNum)
    }

    val query =
      DBIOAction.seq(
        insertTransactions(transactions),
        insertOutputs(outputs),
        insertInputs(inputs),
        insertBlockHeaders(blockHeaders)
      )

    query.transactionally
  }

  def getMainChain(
      blockHash: BlockHash
  )(implicit
      ec: ExecutionContext
  ): DBActionR[Option[Boolean]] = {
    sql"""
      SELECT main_chain FROM block_headers
      WHERE hash = $blockHash
    """.asAS[Boolean].headOrNone
  }
  def getBlockTimes(
      fromGroup: GroupIndex,
      toGroup: GroupIndex,
      after: TimeStamp
  ): DBActionSR[TimeStamp] = {
    sql"""
      SELECT block_timestamp FROM  block_headers
      WHERE chain_from = $fromGroup AND chain_to = $toGroup AND block_timestamp > $after
      ORDER BY block_timestamp
    """.asAS[TimeStamp]
  }

  /** Fetches the maximum `block_timestamp` from blocks with maximum height within the given chain.
    */
  val maxBlockTimestampForMaxHeightForChain: String =
    s"""
       |SELECT max(block_timestamp) as block_timestamp,
       |       height
       |FROM $block_headers
       |WHERE height = (SELECT max(height)
       |                FROM $block_headers
       |                WHERE chain_from = ?
       |                  AND chain_to = ?)
       |  AND chain_from = ?
       |  AND chain_to = ?
       |GROUP BY height
       |""".stripMargin

  /** Fetches the maximum `block_timestamp` and sum height of all blocks filtered with maximum
    * height for all input chain chains provided by [[GroupSetting.chainIndexes]]
    *
    * @param groupSetting
    *   Provides the list of chains to run this query on
    * @return
    *   Maximum timestamp and sum of all heights i.e. the total number of blocks.
    */
  def numOfBlocksAndMaxBlockTimestamp()(implicit
      groupSetting: GroupSetting,
      ec: ExecutionContext
  ): DBActionR[Option[(TimeStamp, Height)]] = {
    // build queries for each chainFrom-chainTo and merge them into a single UNION query
    val unions: String =
      Array
        .fill(groupSetting.chainIndexes.size)(maxBlockTimestampForMaxHeightForChain)
        .mkString("UNION")

    // fetch maximum `block_timestamp` and sum all `heights` for all unions
    val query =
      s"""
         SELECT max(block_timestamp),
                sum(height)
         FROM ($unions) as max_block_timestamps_and_num_of_blocks;
         """

    val parameters: SetParameter[Unit] =
      (_: Unit, params: PositionedParameters) =>
        groupSetting.chainIndexes foreach { chainIndex =>
          params >> chainIndex.from
          params >> chainIndex.to
          params >> chainIndex.from
          params >> chainIndex.to
        }

    SQLActionBuilder(
      sql = query,
      setParameter = parameters
    ).asAS[Option[(TimeStamp, Height)]].oneOrNone
  }

  /** Fetches maximum `Height` for each `GroupIndex` pair.
    *
    * @see
    *   [[maxHeightZipped]] for paired output with input.
    *
    * @return
    *   A collection of optional `Height` values in the same order as the input `GroupIndex` pair.
    *   Collection items will be `Some(height)` when `GroupIndex` pair exists, else `None`.
    */
  def maxHeight(
      chainIndexes: Iterable[ChainIndex]
  )(implicit ec: ExecutionContext): DBActionR[ArraySeq[Option[Height]]] =
    if (chainIndexes.isEmpty) {
      DBIOAction.successful(ArraySeq.empty)
    } else {
      // Parameter index is used for ordering the output collection
      def maxHeight(index: Int): String =
        s"""
           SELECT max(height), $index
           FROM $block_headers
           WHERE chain_from = ?
             AND chain_to = ?
           """.stripMargin

      val query =
        Array
          .tabulate(chainIndexes.size)(maxHeight)
          .mkString("UNION ALL")

      val parameters: SetParameter[Unit] =
        (_: Unit, params: PositionedParameters) =>
          chainIndexes foreach { chainIndex =>
            params >> chainIndex.from
            params >> chainIndex.to
          }

      val queryResult =
        SQLActionBuilder(
          sql = query,
          setParameter = parameters
        ).asAS[(Option[Height], Int)]

      // Sort by index and then return just the heights
      queryResult.map(_.sortBy(_._2).map(_._1))
    }

  /** Pairs input to it's output value */
  def maxHeightZipped(chainIndexes: Iterable[ChainIndex])(implicit
      ec: ExecutionContext
  ): DBActionR[ArraySeq[(Option[Height], ChainIndex)]] =
    maxHeight(chainIndexes).map(_.zip(chainIndexes))
}
