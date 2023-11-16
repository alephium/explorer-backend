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

import org.alephium.explorer.persistence._
import org.alephium.explorer.persistence.model.{InputEntity, TokenInputEntity}
import org.alephium.explorer.persistence.schema.CustomGetResult._
import org.alephium.explorer.persistence.schema.CustomSetParameter._
import org.alephium.explorer.util.SlickUtil._

object InputUpdateQueries {

  private type UpdateReturn = (InputEntity, Boolean)

  def updateInputs()(implicit ec: ExecutionContext): DBActionWT[Unit] = {
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
      RETURNING inputs.block_hash, inputs.tx_hash, inputs.block_timestamp,
                inputs.hint, inputs.output_ref_key, inputs.unlock_script,
                inputs.main_chain, inputs.input_order, inputs.tx_order,
                outputs.tx_hash, outputs.address, outputs.amount,
                outputs.tokens, outputs.coinbase"""
      .asASE[UpdateReturn](inputWithBooleanGetResult)
      .flatMap(internalUpdates)
      .transactionally
  }

  // format: off
  private def internalUpdates(
      data: ArraySeq[UpdateReturn])
    : DBActionWT[Unit] = {
  // format: on
    DBIOAction
      .seq(
        updateTransactionPerAddresses(data),
        updateTokenTxPerAddresses(data),
        insertTokenInputs(data)
      )
      .transactionally
  }

  // format: off
  private def updateTransactionPerAddresses(
      data: ArraySeq[UpdateReturn])
    : DBActionWT[Int] = {
  // format: on
    QuerySplitter.splitUpdates(rows = data, columnsPerRow = 7) { (data, placeholder) =>
      val query =
        s"""
           | INSERT INTO transaction_per_addresses (address, tx_hash, block_hash, block_timestamp, tx_order, main_chain, coinbase)
           | VALUES $placeholder
           | ON CONFLICT ON CONSTRAINT txs_per_address_pk DO NOTHING
           |""".stripMargin

      val parameters: SetParameter[Unit] =
        (_: Unit, params: PositionedParameters) =>
          data foreach { case (input, coinbase) =>
            params >> input.outputRefAddress
            params >> input.txHash
            params >> input.blockHash
            params >> input.timestamp
            params >> input.txOrder
            params >> input.mainChain
            params >> coinbase
          }

      SQLActionBuilder(
        queryParts = query,
        unitPConv = parameters
      ).asUpdate
    }
  }

  // format: off
  private def updateTokenTxPerAddresses(
      data: ArraySeq[UpdateReturn])
    : DBActionWT[Int] = {
  // format: on
    val tokenTxPerAddresses = data.flatMap { case (input, _) =>
      input.outputRefTokens match {
        case None => ArraySeq.empty
        case Some(tokens) =>
          tokens.map { token =>
            (
              input.outputRefAddress,
              input.txHash,
              input.blockHash,
              input.timestamp,
              input.txOrder,
              input.mainChain,
              token.id
            )
          }
      }
    }

    QuerySplitter.splitUpdates(rows = tokenTxPerAddresses, columnsPerRow = 7) {
      (tokenTxPerAddresses, placeholder) =>
        val query =
          s"""
             | INSERT INTO token_tx_per_addresses (address, tx_hash, block_hash, block_timestamp, tx_order, main_chain, token)
             | VALUES $placeholder
             | ON CONFLICT ON CONSTRAINT token_tx_per_address_pk DO NOTHING
             |""".stripMargin

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
          queryParts = query,
          unitPConv = parameters
        ).asUpdate
    }
  }

  // format: off
  def insertTokenInputs(
      data: ArraySeq[UpdateReturn])
    : DBActionWT[Int] = {
  // format: on
    val tokenInputs = data.flatMap { case (input, _) =>
      input.outputRefTokens match {
        case None => ArraySeq.empty
        case Some(tokens) =>
          tokens.map { token =>
            TokenInputEntity(
              input.blockHash,
              input.txHash,
              input.timestamp,
              input.hint,
              input.outputRefKey,
              input.unlockScript,
              input.mainChain,
              input.inputOrder,
              input.txOrder,
              input.outputRefTxHash,
              input.outputRefAddress,
              input.outputRefAmount,
              token.id,
              token.amount
            )
          }
      }
    }

    TokenQueries.insertTokenInputs(tokenInputs)
  }
}
