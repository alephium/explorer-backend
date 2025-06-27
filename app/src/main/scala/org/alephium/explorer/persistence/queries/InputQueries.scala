// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.queries

import scala.collection.immutable.ArraySeq
import scala.concurrent.ExecutionContext

import akka.util.ByteString
import slick.dbio.DBIOAction
import slick.jdbc._
import slick.jdbc.PostgresProfile.api._

import org.alephium.api.model.{Address => ApiAddress}
import org.alephium.explorer.persistence._
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.persistence.queries.result.{InputsFromTxQR, InputsQR}
import org.alephium.explorer.persistence.schema.CustomGetResult._
import org.alephium.explorer.persistence.schema.CustomSetParameter._
import org.alephium.explorer.util.SlickExplainUtil._
import org.alephium.explorer.util.SlickUtil._
import org.alephium.protocol.model.{BlockHash, TransactionId}

object InputQueries {

  /** Inserts inputs or ignore rows with primary key conflict */
  // scalastyle:off magic.number
  def insertInputs(inputs: Iterable[InputEntity]): DBActionW[Int] =
    QuerySplitter.splitUpdates(rows = inputs, columnsPerRow = 14) { (inputs, placeholder) =>
      val query =
        s"""
           INSERT INTO inputs ("block_hash",
                               "tx_hash",
                               "block_timestamp",
                               "hint",
                               "output_ref_key",
                               "unlock_script",
                               "main_chain",
                               "input_order",
                               "tx_order",
                               "output_ref_tx_hash",
                               "output_ref_address",
                               "output_ref_groupless_address",
                               "output_ref_amount",
                               "contract_input")
           VALUES $placeholder
           ON CONFLICT
               ON CONSTRAINT inputs_pk
               DO NOTHING
           """.stripMargin

      val parameters: SetParameter[Unit] =
        (_: Unit, params: PositionedParameters) =>
          inputs foreach { input =>
            params >> input.blockHash
            params >> input.txHash
            params >> input.timestamp
            params >> input.hint
            params >> input.outputRefKey
            params >> input.unlockScript
            params >> input.mainChain
            params >> input.inputOrder
            params >> input.txOrder
            params >> input.outputRefTxHash
            params >> input.outputRefAddress
            params >> input.outputRefGrouplessAddress
            params >> input.outputRefAmount
            params >> input.contractInput
          }

      SQLActionBuilder(
        sql = query,
        setParameter = parameters
      ).asUpdate
    }

  def inputsFromTxs(
      hashes: ArraySeq[(TransactionId, BlockHash)]
  ): DBActionR[ArraySeq[InputsFromTxQR]] =
    if (hashes.nonEmpty) {
      inputsFromTxsBuilder(hashes).asAS[InputsFromTxQR]
    } else {
      DBIOAction.successful(ArraySeq.empty)
    }

  private def inputsFromTxsBuilder(
      hashes: ArraySeq[(TransactionId, BlockHash)]
  ): SQLActionBuilder = {
    val params = paramPlaceholderTuple2(1, hashes.size)

    val query =
      s"""
         SELECT ${InputsFromTxQR.selectFields}
         FROM inputs
         WHERE (tx_hash, block_hash) IN $params

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
    )
  }

  def getInputsQuery(txHash: TransactionId, blockHash: BlockHash): DBActionSR[InputsQR] =
    sql"""
        SELECT #${InputsQR.selectFields}
        FROM inputs
        WHERE tx_hash = $txHash
          AND block_hash = $blockHash
        ORDER BY input_order
    """.asAS[InputsQR]

  def getMainChainInputs(ascendingOrder: Boolean): DBActionSR[InputEntity] =
    sql"""
        SELECT
          block_hash,
          tx_hash,
          block_timestamp,
          hint,
          output_ref_key,
          unlock_script,
          main_chain,
          input_order,
          tx_order,
          output_ref_tx_hash,
          output_ref_address,
          output_ref_groupless_address,
          output_ref_amount,
          output_ref_tokens,
          contract_input
        FROM inputs
        WHERE main_chain = true
        ORDER BY block_timestamp #${if (ascendingOrder) "" else "DESC"}
    """.asASE[InputEntity](inputGetResult)

  def getUnlockScript(address: ApiAddress)(implicit
      ec: ExecutionContext
  ): DBActionR[Option[ByteString]] = {
    sql"""
      select unlock_script
      FROM inputs
      WHERE #${addressColumn(
        address,
        "output_ref_address",
        "output_ref_groupless_address"
      )}  = $address
      LIMIT 1
    """.asAS[ByteString].headOrNone
  }

  /** Runs explain on query `inputsFromTxs` and checks the index `inputs_tx_hash_block_hash_idx` is
    * being used
    */
  def explainInputsFromTxs(
      hashes: ArraySeq[(TransactionId, BlockHash)]
  )(implicit ec: ExecutionContext): DBActionR[ExplainResult] = {
    val queryName = "inputsFromTxs"
    if (hashes.isEmpty) {
      DBIOAction.successful(ExplainResult.emptyInput(queryName))
    } else {
      inputsFromTxsBuilder(hashes).explainAnalyze() map { explain =>
        ExplainResult(
          queryName = queryName,
          queryInput = hashes.toString(),
          explain = explain,
          messages = Iterable.empty,
          passed = explain.exists(_.contains("inputs_tx_hash_block_hash_idx"))
        )
      }
    }
  }
}
