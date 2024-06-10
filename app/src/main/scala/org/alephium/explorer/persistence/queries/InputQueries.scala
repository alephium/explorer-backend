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

import akka.util.ByteString
import slick.dbio.DBIOAction
import slick.jdbc._
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.persistence._
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.persistence.queries.result.{InputsFromTxQR, InputsQR}
import org.alephium.explorer.persistence.schema.CustomGetResult._
import org.alephium.explorer.persistence.schema.CustomSetParameter._
import org.alephium.explorer.util.SlickExplainUtil._
import org.alephium.explorer.util.SlickUtil._
import org.alephium.protocol.model.{Address, BlockHash, TransactionId}

object InputQueries {

  /** Inserts inputs or ignore rows with primary key conflict */
  // scalastyle:off magic.number
  def insertInputs(inputs: Iterable[InputEntity]): DBActionW[Int] =
    QuerySplitter.splitUpdates(rows = inputs, columnsPerRow = 13) { (inputs, placeholder) =>
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
         SELECT tx_hash,
                input_order,
                hint,
                output_ref_key,
                unlock_script,
                output_ref_tx_hash,
                output_ref_address,
                output_ref_amount,
                output_ref_tokens,
                contract_input
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
        SELECT hint,
               output_ref_key,
               unlock_script,
               output_ref_tx_hash,
               output_ref_address,
               output_ref_amount,
               output_ref_tokens,
               contract_input
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
          output_ref_amount,
          output_ref_tokens,
          contract_input
        FROM inputs
        WHERE main_chain = true
        ORDER BY block_timestamp #${if (ascendingOrder) "" else "DESC"}
    """.asASE[InputEntity](inputGetResult)

  def getUnlockScript(address: Address)(implicit
      ec: ExecutionContext
  ): DBActionR[Option[ByteString]] = {
    sql"""
      select unlock_script
      FROM inputs
      WHERE output_ref_address = $address
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
