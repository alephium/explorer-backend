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
import org.alephium.explorer.persistence.schema.CustomGetResult._
import org.alephium.explorer.persistence.schema.CustomSetParameter._
import org.alephium.protocol.model.{Address, BlockHash, TransactionId}
import org.alephium.util._

object InputUpdateQueries {

  private type UpdateReturn =
    (Address, Option[ArraySeq[Token]], TransactionId, BlockHash, TimeStamp, Int, Boolean)

  def updateInputs()(implicit ec: ExecutionContext): DBActionWT[Unit] = {
    (for {
      fixed            <- updateFixedInputs()
      mutableContracts <- updateMutableContractInputs()
      contracts        <- updateContractInputs()
      _                <- internalUpdates(fixed ++ contracts ++ mutableContracts)
    } yield {}).transactionally
  }

  /*
   * Updates the inputs table with information from fixed (immutable) outputs.
   *
   * This function handles cases where the output is guaranteed to be immutable, meaning:
   * - Each output reference key (`output_ref_key`) is unique.
   * - The output's amount, tokens, and address will always remain the same for a given key.
   */
  private def updateFixedInputs() = {
    sql"""
      UPDATE inputs
      SET
        output_ref_tx_hash = outputs.tx_hash,
        output_ref_address = outputs.address,
        output_ref_amount = outputs.amount,
        output_ref_tokens = outputs.tokens
      FROM outputs
      WHERE inputs.output_ref_key = outputs.key
      AND inputs.output_ref_amount IS NULL
      AND inputs.contract_input = false
      AND outputs.fixed_output = true
      RETURNING outputs.address, outputs.tokens, inputs.tx_hash, inputs.block_hash, inputs.block_timestamp, inputs.tx_order, inputs.main_chain
    """
      .as[UpdateReturn]
  }

  /*
   * Updates the inputs table with information from mutable contract outputs.
   *
   * This function handles cases where:
   * - The referenced contract outputs are mutable, meaning their amount can differ for the same key
   *   based on blockchain context (e.g., main chain vs. side chains).
   * - Each input-output pair may have different amounts, depending on whether it's
   *   from the main chain, side chain, or an uncle block.
   */
  private def updateMutableContractInputs() = {
    sql"""
      UPDATE inputs
      SET
        output_ref_tx_hash = outputs.tx_hash,
        output_ref_address = outputs.address,
        output_ref_amount = outputs.amount,
        output_ref_tokens = outputs.tokens
      FROM outputs
      WHERE inputs.output_ref_key = outputs.key
      AND inputs.output_ref_amount IS NULL
      AND inputs.main_chain = outputs.main_chain
      AND inputs.contract_input = true
      AND outputs.fixed_output = false
      RETURNING outputs.address, outputs.tokens, inputs.tx_hash, inputs.block_hash, inputs.block_timestamp, inputs.tx_order, inputs.main_chain
    """
      .as[UpdateReturn]
  }

  /*
   * Updates the inputs table for contract outputs where the amount is the same
   * between main chain and side chain outputs.
   *
   * This function is similar to `updateMutableContractInputs`, but it does **not**
   * require the `main_chain` status to match between inputs and outputs.
   * This is useful for general contract outputs not covered by `updateMutableContractInputs`.
   */
  private def updateContractInputs() = {
    sql"""
      UPDATE inputs
      SET
        output_ref_tx_hash = outputs.tx_hash,
        output_ref_address = outputs.address,
        output_ref_amount = outputs.amount,
        output_ref_tokens = outputs.tokens
      FROM outputs
      WHERE inputs.output_ref_key = outputs.key
      AND inputs.output_ref_amount IS NULL
      AND inputs.contract_input = true
      AND outputs.fixed_output = false
      RETURNING outputs.address, outputs.tokens, inputs.tx_hash, inputs.block_hash, inputs.block_timestamp, inputs.tx_order, inputs.main_chain
    """
      .as[UpdateReturn]
  }

  // format: off
  private def internalUpdates(
      data: Vector[UpdateReturn])
    : DBActionWT[Unit] = {
  // format: on
    DBIOAction
      .seq(
        updateTransactionPerAddresses(data),
        updateTokenTxPerAddresses(data)
      )
      .transactionally
  }

  // format: off
  private def updateTransactionPerAddresses(
      data: Vector[UpdateReturn])
    : DBActionWT[Int] = {
  // format: on
    QuerySplitter.splitUpdates(rows = data, columnsPerRow = 7) { (data, placeholder) =>
      val query =
        s"""
            INSERT INTO transaction_per_addresses (address, tx_hash, block_hash, block_timestamp, tx_order, main_chain, coinbase)
            VALUES $placeholder
            ON CONFLICT ON CONSTRAINT txs_per_address_pk DO NOTHING
           """.stripMargin

      val parameters: SetParameter[Unit] =
        (_: Unit, params: PositionedParameters) =>
          data foreach { case (address, _, txHash, blockHash, timestamp, txOrder, mainChain) =>
            params >> address
            params >> txHash
            params >> blockHash
            params >> timestamp
            params >> txOrder
            params >> mainChain
            params >> false
          }

      SQLActionBuilder(
        sql = query,
        setParameter = parameters
      ).asUpdate
    }
  }

  // format: off
  private def updateTokenTxPerAddresses(
      data: Vector[UpdateReturn])
    : DBActionWT[Int] = {
  // format: on
    val tokenTxPerAddresses = data.flatMap {
      case (address, tokensOpt, txHash, blockHash, timestamp, txOrder, mainChain) =>
        tokensOpt match {
          case None => ArraySeq.empty
          case Some(tokens) =>
            tokens.map { token =>
              (address, txHash, blockHash, timestamp, txOrder, mainChain, token.id)
            }
        }
    }

    QuerySplitter.splitUpdates(rows = tokenTxPerAddresses, columnsPerRow = 7) {
      (tokenTxPerAddresses, placeholder) =>
        val query =
          s"""
              INSERT INTO token_tx_per_addresses (address, tx_hash, block_hash, block_timestamp, tx_order, main_chain, token)
              VALUES $placeholder
              ON CONFLICT ON CONSTRAINT token_tx_per_address_pk DO NOTHING
             """

        val parameters: SetParameter[Unit] =
          (_: Unit, params: PositionedParameters) =>
            tokenTxPerAddresses foreach {
              case (address, txHash, blockHash, timestamp, txOrder, mainChain, token) =>
                params >> address
                params >> txHash
                params >> blockHash
                params >> timestamp
                params >> txOrder
                params >> mainChain
                params >> token
            }

        SQLActionBuilder(
          sql = query,
          setParameter = parameters
        ).asUpdate
    }
  }
}
