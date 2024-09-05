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
import scala.concurrent.ExecutionContext

import slick.dbio.DBIOAction
import slick.jdbc.{PositionedParameters, SetParameter, SQLActionBuilder}
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence._
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.persistence.queries.result.{OutputsFromTxQR, OutputsQR}
import org.alephium.explorer.persistence.schema.CustomGetResult._
import org.alephium.explorer.persistence.schema.CustomSetParameter._
import org.alephium.explorer.util.SlickExplainUtil._
import org.alephium.explorer.util.SlickUtil._
import org.alephium.protocol.Hash
import org.alephium.protocol.model.{Address, BlockHash, TransactionId}
import org.alephium.util.{TimeStamp, U256}

object OutputQueries {

  def insertOutputs(outputs: Iterable[OutputEntity]): DBActionRWT[Unit] =
    DBIOAction
      .seq(
        insertBasicOutputs(outputs),
        insertTxPerAddressFromOutputs(outputs),
        insertTokensFromOutputs(outputs)
      )
      .transactionally

  /** Inserts outputs or ignore rows with primary key conflict */
  // scalastyle:off magic.number method.length
  private def insertBasicOutputs(outputs: Iterable[OutputEntity]): DBActionW[Int] =
    QuerySplitter
      .splitUpdates(rows = outputs, columnsPerRow = 18) { (outputs, placeholder) =>
        val query =
          s"""
             INSERT INTO outputs ("block_hash",
                                  "tx_hash",
                                  "block_timestamp",
                                  "output_type",
                                  "hint",
                                  "key",
                                  "amount",
                                  "address",
                                  "tokens",
                                  "main_chain",
                                  "lock_time",
                                  "message",
                                  "output_order",
                                  "tx_order",
                                  "coinbase",
                                  "spent_finalized",
                                  "spent_timestamp",
                                  "fixed_output")
             VALUES $placeholder
             ON CONFLICT
                 ON CONSTRAINT outputs_pk
                 DO NOTHING
             """

        val parameters: SetParameter[Unit] =
          (_: Unit, params: PositionedParameters) =>
            outputs foreach { output =>
              params >> output.blockHash
              params >> output.txHash
              params >> output.timestamp
              params >> output.outputType
              params >> output.hint
              params >> output.key
              params >> output.amount
              params >> output.address
              params >> output.tokens
              params >> output.mainChain
              params >> output.lockTime
              params >> output.message
              params >> output.outputOrder
              params >> output.txOrder
              params >> output.coinbase
              params >> output.spentFinalized
              params >> output.spentTimestamp
              params >> output.fixedOutput
            }

        SQLActionBuilder(
          sql = query,
          setParameter = parameters
        ).asUpdate
      }
  // scalastyle:on magic.number

  private def insertTxPerAddressFromOutputs(outputs: Iterable[OutputEntity]): DBActionW[Int] = {
    QuerySplitter.splitUpdates(rows = outputs, columnsPerRow = 7) { (outputs, placeholder) =>
      val query =
        s"""
           INSERT INTO transaction_per_addresses (address, tx_hash, block_hash, block_timestamp, tx_order, main_chain, coinbase)
           VALUES $placeholder
           ON CONFLICT (tx_hash, block_hash, address)
           DO NOTHING
           """

      val parameters: SetParameter[Unit] =
        (_: Unit, params: PositionedParameters) =>
          outputs foreach { output =>
            params >> output.address
            params >> output.txHash
            params >> output.blockHash
            params >> output.timestamp
            params >> output.txOrder
            params >> output.mainChain
            params >> output.coinbase
          }

      SQLActionBuilder(
        sql = query,
        setParameter = parameters
      ).asUpdate
    }
  }

  private def insertTokensFromOutputs(outputs: Iterable[OutputEntity]): DBActionRWT[Unit] = {
    val tokenOutputs = outputs.flatMap { output =>
      output.tokens match {
        case None => Iterable.empty
        case Some(tokens) =>
          tokens.map(token => (token, output))
      }
    }

    DBIOAction
      .seq(
        insertTokenOutputs(tokenOutputs),
        insertTransactionTokenFromOutputs(tokenOutputs),
        insertTokenPerAddressFromOutputs(tokenOutputs),
        insertTokenInfoFromOutputs(tokenOutputs)
      )
      .transactionally
  }

  // scalastyle:off magic.number
  private def insertTokenOutputs(tokenOutputs: Iterable[(Token, OutputEntity)]): DBActionW[Int] = {
    QuerySplitter.splitUpdates(rows = tokenOutputs, columnsPerRow = 16) {
      (tokenOutputs, placeholder) =>
        val query =
          s"""
             INSERT INTO token_outputs ("block_hash",
                                  "tx_hash",
                                  "block_timestamp",
                                  "output_type",
                                  "hint",
                                  "key",
                                  "token",
                                  "amount",
                                  "address",
                                  "main_chain",
                                  "lock_time",
                                  "message",
                                  "output_order",
                                  "tx_order",
                                  "spent_finalized",
                                  "spent_timestamp")
             VALUES $placeholder
             ON CONFLICT
                 ON CONSTRAINT token_outputs_pk
                 DO NOTHING
             """

        val parameters: SetParameter[Unit] =
          (_: Unit, params: PositionedParameters) =>
            tokenOutputs foreach { case (token, output) =>
              params >> output.blockHash
              params >> output.txHash
              params >> output.timestamp
              params >> output.outputType
              params >> output.hint
              params >> output.key
              params >> token.id
              params >> token.amount
              params >> output.address
              params >> output.mainChain
              params >> output.lockTime
              params >> output.message
              params >> output.outputOrder
              params >> output.txOrder
              params >> output.spentFinalized
              params >> output.spentTimestamp
            }

        SQLActionBuilder(
          sql = query,
          setParameter = parameters
        ).asUpdate
    }
  }
  // scalastyle:on magic.number

  private def insertTransactionTokenFromOutputs(
      tokenOutputs: Iterable[(Token, OutputEntity)]
  ): DBActionW[Int] = {
    QuerySplitter.splitUpdates(rows = tokenOutputs, columnsPerRow = 6) {
      (tokenOutputs, placeholder) =>
        val query =
          s"""
               INSERT INTO transaction_per_token (tx_hash, block_hash, token, block_timestamp, tx_order, main_chain )
               VALUES $placeholder
               ON CONFLICT (tx_hash, block_hash, token) DO NOTHING
             """

        val parameters: SetParameter[Unit] =
          (_: Unit, params: PositionedParameters) =>
            tokenOutputs foreach { case (token, output) =>
              params >> output.txHash
              params >> output.blockHash
              params >> token
              params >> output.timestamp
              params >> output.txOrder
              params >> output.mainChain
            }

        SQLActionBuilder(
          sql = query,
          setParameter = parameters
        ).asUpdate
    }
  }

  private def insertTokenPerAddressFromOutputs(
      tokenOutputs: Iterable[(Token, OutputEntity)]
  ): DBActionW[Int] = {
    QuerySplitter.splitUpdates(rows = tokenOutputs, columnsPerRow = 7) {
      (tokenOutputs, placeholder) =>
        val query =
          s"""
             INSERT INTO token_tx_per_addresses (address, tx_hash, block_hash, block_timestamp, tx_order, main_chain, token)
             VALUES $placeholder
             ON CONFLICT (tx_hash, block_hash, address, token)
             DO NOTHING
             """

        val parameters: SetParameter[Unit] =
          (_: Unit, params: PositionedParameters) =>
            tokenOutputs foreach { case (token, output) =>
              params >> output.address
              params >> output.txHash
              params >> output.blockHash
              params >> output.timestamp
              params >> output.txOrder
              params >> output.mainChain
              params >> token
            }

        SQLActionBuilder(
          sql = query,
          setParameter = parameters
        ).asUpdate
    }
  }

  @SuppressWarnings(Array("org.wartremover.warts.IterableOps"))
  private def insertTokenInfoFromOutputs(
      tokenOutputs: Iterable[(Token, OutputEntity)]
  ): DBActionW[Int] = {
    val tokens = tokenOutputs
      .groupBy { case (token, _) => token.id }
      .map { case (token, groups) =>
        val timestamp = groups.map { case (_, output) => output.timestamp }.max
        (token, timestamp)
      }

    QuerySplitter.splitUpdates(rows = tokens, columnsPerRow = 2) { (tokens, placeholder) =>
      val query =
        s"""
           INSERT INTO token_info (token, last_used)
           VALUES $placeholder
           ON CONFLICT
           ON CONSTRAINT token_info_pkey
           DO UPDATE
           SET last_used = EXCLUDED.last_used
           """

      val parameters: SetParameter[Unit] =
        (_: Unit, params: PositionedParameters) =>
          tokens foreach { case (token, timestamp) =>
            params >> token
            params >> timestamp
          }

      SQLActionBuilder(
        sql = query,
        setParameter = parameters
      ).asUpdate
    }
  }

  def outputsFromTxs(
      hashes: ArraySeq[(TransactionId, BlockHash)]
  ): DBActionR[ArraySeq[OutputsFromTxQR]] =
    if (hashes.nonEmpty) {
      val params = paramPlaceholderTuple2(1, hashes.size)

      val query =
        s"""
           SELECT ${OutputsFromTxQR.selectFields}
           FROM outputs
           WHERE (outputs.tx_hash, outputs.block_hash) IN $params
           """

      val parameters: SetParameter[Unit] =
        (_: Unit, params: PositionedParameters) =>
          hashes foreach { case (txnHash, blockHash) =>
            params >> txnHash
            params >> blockHash
          }

      SQLActionBuilder(
        sql = query,
        setParameter = parameters
      ).asAS[OutputsFromTxQR]
    } else {
      DBIOAction.successful(ArraySeq.empty)
    }

  def getOutputsQuery(txHash: TransactionId, blockHash: BlockHash): DBActionSR[OutputsQR] =
    sql"""
        SELECT #${OutputsQR.selectFields}
        FROM outputs
        WHERE tx_hash = $txHash
          AND block_hash = $blockHash
        ORDER BY output_order
      """.asAS[OutputsQR]

  /** Get main chain [[org.alephium.explorer.persistence.model.OutputEntity]]s ordered by timestamp
    */
  def getMainChainOutputs(ascendingOrder: Boolean): DBActionSR[OutputEntity] = {
    sql"""
      SELECT
        block_hash,
        tx_hash,
        block_timestamp,
        output_type,
        hint,
        key,
        amount,
        address,
        tokens,
        main_chain,
        lock_time,
        message,
        output_order,
        tx_order,
        coinbase,
        spent_finalized,
        spent_timestamp,
        fixed_output
      FROM outputs
      WHERE main_chain = true
      ORDER BY block_timestamp #${if (ascendingOrder) "" else "DESC"}
      """.asASE[OutputEntity](outputGetResult)
  }

  /** Checks that [[getTxnHash]] uses both indexes for the given key */
  def explainGetTxnHash(
      key: Option[Hash]
  )(implicit ec: ExecutionContext): DBActionR[ExplainResult] = {
    val queryName = "getTxnHashBuilder"

    key match {
      case Some(key) =>
        getTxnHashBuilder(key).explainAnalyze() map { explain =>
          val explainString               = explain.mkString
          val outputs_pk_used             = explainString contains "outputs_pk"
          val outputs_main_chain_idx_used = explainString contains "outputs_main_chain_idx"
          val passed                      = outputs_pk_used && outputs_main_chain_idx_used
          val message =
            ArraySeq(
              s"Used outputs_main_chain_idx = $outputs_main_chain_idx_used",
              s"Used outputs_pk             = $outputs_pk_used"
            )

          ExplainResult(
            queryName = queryName,
            queryInput = key.toString(),
            explain = explain,
            messages = message,
            passed = passed
          )
        }

      case None =>
        DBIOAction.successful(ExplainResult.emptyInput(queryName))
    }
  }

  def getTxnHash(key: Hash): DBActionSR[TransactionId] =
    getTxnHashBuilder(key).asAS[TransactionId]

  /** Fetch `tx_hash` for keys where `main_chain` is true */
  def getTxnHashBuilder(key: Hash): SQLActionBuilder =
    sql"""
      SELECT tx_hash
      FROM outputs
      WHERE main_chain = true
        AND key = $key
    """

  def getBalanceActionOption(
      address: Address
  )(implicit ec: ExecutionContext): DBActionR[(Option[U256], Option[U256])] =
    getBalanceUntilLockTime(
      address = address,
      lockTime = TimeStamp.now()
    )

  def getBalanceUntilLockTime(address: Address, lockTime: TimeStamp)(implicit
      ec: ExecutionContext
  ): DBActionR[(Option[U256], Option[U256])] =
    sql"""
      SELECT sum(outputs.amount),
             sum(CASE
                     WHEN outputs.lock_time is NULL or outputs.lock_time < ${lockTime.millis} THEN 0
                     ELSE outputs.amount
                 END)
      FROM outputs
               LEFT JOIN inputs
                         ON outputs.key = inputs.output_ref_key
                             AND inputs.main_chain = true
      WHERE outputs.spent_finalized IS NULL
        AND outputs.address = $address
        AND outputs.main_chain = true
        AND inputs.block_hash IS NULL;
    """.asAS[(Option[U256], Option[U256])].exactlyOne

}
