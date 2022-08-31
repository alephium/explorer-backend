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
import scala.concurrent.ExecutionContext

import com.typesafe.scalalogging.StrictLogging
import slick.dbio.DBIOAction
import slick.jdbc.{PositionedParameters, SetParameter, SQLActionBuilder}
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.GroupSetting
import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence._
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.persistence.queries.BlockDepQueries._
import org.alephium.explorer.persistence.queries.InputQueries.insertInputs
import org.alephium.explorer.persistence.queries.OutputQueries.insertOutputs
import org.alephium.explorer.persistence.queries.TransactionQueries._
import org.alephium.explorer.persistence.schema._
import org.alephium.explorer.persistence.schema.CustomGetResult._
import org.alephium.explorer.persistence.schema.CustomJdbcTypes._
import org.alephium.explorer.persistence.schema.CustomSetParameter._
import org.alephium.explorer.util.SlickExplainUtil._
import org.alephium.explorer.util.SlickUtil._
import org.alephium.util.TimeStamp

object BlockQueries extends StrictLogging {

  @SuppressWarnings(Array("org.wartremover.warts.PublicInference"))
  val block_headers = BlockHeaderSchema.table.baseTableRow.tableName //block_headers table name

  @SuppressWarnings(Array("org.wartremover.warts.PublicInference"))
  val mainChainQuery = BlockHeaderSchema.table.filter(_.mainChain)

  def explainMainChainQuery()(implicit ec: ExecutionContext): DBActionR[ExplainResult] =
    mainChainQuery.result.explainAnalyze() map { explain =>
      ExplainResult(
        queryName  = "mainChainQuery",
        queryInput = "Unit",
        explain    = explain,
        messages   = Iterable.empty,
        passed     = explain.mkString contains "block_headers_main_chain_idx"
      )
    }

  def buildBlockEntryAction(blockHeader: BlockHeader)(
      implicit ec: ExecutionContext): DBActionR[BlockEntry] =
    for {
      deps <- getDepsForBlock(blockHeader.hash).result
      txs  <- getTransactionsByBlockHash(blockHeader.hash)
    } yield blockHeader.toApi(deps, txs)

  def getBlockEntryLiteAction(hash: BlockEntry.Hash)(
      implicit ec: ExecutionContext): DBActionR[Option[BlockEntryLite]] =
    for {
      header <- BlockHeaderSchema.table.filter(_.hash === hash).result.headOption
    } yield header.map(_.toLiteApi)

  def getBlockEntryAction(hash: BlockEntry.Hash)(
      implicit ec: ExecutionContext): DBActionR[Option[BlockEntry]] =
    for {
      headers <- BlockHeaderSchema.table.filter(_.hash === hash).result
      blocks  <- DBIOAction.sequence(headers.map(buildBlockEntryAction))
    } yield blocks.headOption

  def getBlockHeaderAction(hash: BlockEntry.Hash): DBActionR[Option[BlockHeader]] =
    sql"""
       |SELECT *
       |FROM #$block_headers
       |WHERE hash = $hash
       |""".stripMargin
      .as[BlockHeader](blockHeaderGetResult)
      .headOption

  def getAtHeightAction(fromGroup: GroupIndex, toGroup: GroupIndex, height: Height)(
      implicit ec: ExecutionContext): DBActionR[Seq[BlockEntry]] =
    for {
      headers <- BlockHeaderSchema.table
        .filter(header =>
          header.height === height && header.chainFrom === fromGroup && header.chainTo === toGroup)
        .result
      blocks <- DBIOAction.sequence(headers.map(buildBlockEntryAction))
    } yield blocks

  /**
    * Order by query for [[org.alephium.explorer.persistence.schema.BlockHeaderSchema.table]]
    */
  private def orderBySQLString(reverse: Boolean): String =
    if (reverse) {
      s"order by block_timestamp, hash desc"
    } else {
      s"order by block_timestamp desc, hash"
    }

  /** Reverse order by without prefix */
  private val LIST_BLOCKS_ORDER_BY_REVERSE: String =
    orderBySQLString(reverse = true)

  /** Forward order by without prefix */
  private val LIST_BLOCKS_ORDER_BY_FORWARD: String =
    orderBySQLString(reverse = false)

  /**
    * Fetches all main_chain [[org.alephium.explorer.persistence.schema.BlockHeaderSchema.table]] rows
    */
  def listMainChainHeadersWithTxnNumberSQL(
      pagination: Pagination): DBActionRWT[Vector[BlockEntryLite]] =
    listMainChainHeadersWithTxnNumberSQLBuilder(pagination)
      .as[BlockEntryLite](blockEntryListGetResult)

  def explainListMainChainHeadersWithTxnNumber(pagination: Pagination)(
      implicit ec: ExecutionContext): DBActionR[ExplainResult] =
    listMainChainHeadersWithTxnNumberSQLBuilder(pagination).explainAnalyze() map { explain =>
      ExplainResult(
        queryName  = "listMainChainHeadersWithTxnNumber",
        queryInput = pagination.toString,
        explain    = explain,
        messages   = Iterable.empty,
        passed     = explain.mkString contains "block_headers_full_index"
      )
    }

  def listMainChainHeadersWithTxnNumberSQLBuilder(pagination: Pagination): SQLActionBuilder = {
    //order by for inner query
    val orderBy =
      if (pagination.reverse) {
        LIST_BLOCKS_ORDER_BY_REVERSE
      } else {
        LIST_BLOCKS_ORDER_BY_FORWARD
      }

    sql"""
         |select hash,
         |       block_timestamp,
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
  }

  def updateMainChainStatusAction(hash: BlockEntry.Hash, isMainChain: Boolean)(
      implicit ec: ExecutionContext): DBActionRWT[Unit] = {
    val query =
      for {
        _ <- TransactionSchema.table
          .filter(_.blockHash === hash)
          .map(_.mainChain)
          .update(isMainChain)
        _ <- OutputSchema.table
          .filter(_.blockHash === hash)
          .map(_.mainChain)
          .update(isMainChain)
        _ <- InputSchema.table
          .filter(_.blockHash === hash)
          .map(_.mainChain)
          .update(isMainChain)
        _ <- BlockHeaderSchema.table
          .filter(_.hash === hash)
          .map(_.mainChain)
          .update(isMainChain)
        _ <- TransactionPerAddressSchema.table
          .filter(_.blockHash === hash)
          .map(_.mainChain)
          .update(isMainChain)
        _ <- TransactionPerTokenSchema.table
          .filter(_.blockHash === hash)
          .map(_.mainChain)
          .update(isMainChain)
        _ <- TokenPerAddressSchema.table
          .filter(_.blockHash === hash)
          .map(_.mainChain)
          .update(isMainChain)
        _ <- TokenOutputSchema.table
          .filter(_.blockHash === hash)
          .map(_.mainChain)
          .update(isMainChain)
      } yield ()

    query.transactionally
  }

  def buildBlockEntryWithoutTxsAction(blockHeader: BlockHeader)(
      implicit ec: ExecutionContext): DBActionR[BlockEntry] =
    for {
      deps <- getDepsForBlock(blockHeader.hash).result
    } yield blockHeader.toApi(deps, Seq.empty)

  def getBlockEntryWithoutTxsAction(hash: BlockEntry.Hash)(
      implicit ec: ExecutionContext): DBActionR[Option[BlockEntry]] =
    for {
      headers <- BlockHeaderSchema.table.filter(_.hash === hash).result
      blocks  <- DBIOAction.sequence(headers.map(buildBlockEntryWithoutTxsAction))
    } yield blocks.headOption

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
    QuerySplitter.splitUpdates(rows = blocks, columnsPerRow = 14) { (blocks, placeholder) =>
      val query =
        s"""
           |insert into $block_headers ("hash",
           |                            "block_timestamp",
           |                            "chain_from",
           |                            "chain_to",
           |                            "height",
           |                            "main_chain",
           |                            "nonce",
           |                            "block_version",
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
  def insertBlockEntity(blocks: Iterable[BlockEntity], groupNum: Int): DBActionRWT[Unit] = {
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
      DBIOAction.seq(insertBlockDeps(blockDeps),
                     insertTransactions(transactions),
                     insertOutputs(outputs),
                     insertInputs(inputs),
                     insertBlockHeaders(blockHeaders))

    query.transactionally
  }

  def getBlockTimes(fromGroup: GroupIndex,
                    toGroup: GroupIndex,
                    after: TimeStamp): DBActionSR[TimeStamp] = {
    sql"""
      SELECT block_timestamp FROM  block_headers
      WHERE chain_from = $fromGroup AND chain_to = $toGroup AND block_timestamp > $after
      ORDER BY block_timestamp
    """.as[TimeStamp]
  }

  /**
    * Fetches the maximum `block_timestamp` from blocks
    * with maximum height within the given chain.
    */
  val maxBlockTimestampForMaxHeightForChainSQL: String =
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

  /**
    * Fetches the maximum `block_timestamp` and sum height of
    * all blocks filtered with maximum height for all input chain
    * chains provided by [[GroupSetting.groupIndexes]]
    *
    * @param groupSetting Provides the list of chains to run this query on
    * @return Maximum timestamp and sum of all heights i.e. the total number of blocks.
    */
  def noOfBlocksAndMaxBlockTimestamp()(
      implicit groupSetting: GroupSetting,
      ec: ExecutionContext): DBActionR[Option[(TimeStamp, Height)]] = {
    val unions: String =
      Array
        .fill(groupSetting.groupIndexes.size)(maxBlockTimestampForMaxHeightForChainSQL)
        .mkString("UNION")

    val query =
      s"""
        |SELECT max(block_timestamp),
        |       sum(height)
        |FROM ($unions) as max_block_timestamps_and_num_of_blocks;
        |""".stripMargin

    val parameters: SetParameter[Unit] =
      (_: Unit, params: PositionedParameters) =>
        groupSetting.groupIndexes foreach {
          case (fromGroup, toGroup) =>
            params >> fromGroup
            params >> toGroup
            params >> fromGroup
            params >> toGroup
      }

    SQLActionBuilder(
      queryParts = query,
      unitPConv  = parameters
    ).as[Option[(TimeStamp, Height)]].oneOrNone
  }
}
