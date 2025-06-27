// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.queries

import scala.collection.immutable.ArraySeq
import scala.concurrent.ExecutionContext

import slick.dbio.DBIOAction
import slick.jdbc.{PositionedParameters, SetParameter, SQLActionBuilder}
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence._
import org.alephium.explorer.persistence.model.GrouplessAddress
import org.alephium.explorer.persistence.schema.CustomGetResult._
import org.alephium.explorer.persistence.schema.CustomSetParameter._
import org.alephium.protocol.model.{Address, BlockHash, TransactionId}
import org.alephium.util._

object InputUpdateQueries {

  private type UpdateReturn =
    (
        Address,
        Option[GrouplessAddress],
        Option[ArraySeq[Token]],
        TransactionId,
        BlockHash,
        TimeStamp,
        Int,
        Boolean
    )

  def updateInputs()(implicit ec: ExecutionContext): DBActionWT[Unit] = {
    sql"""
      UPDATE inputs
      SET
        output_ref_tx_hash = outputs.tx_hash,
        output_ref_address = outputs.address,
        output_ref_groupless_address = outputs.groupless_address,
        output_ref_amount = outputs.amount,
        output_ref_tokens = outputs.tokens
      FROM outputs
      WHERE inputs.output_ref_key = outputs.key
      AND inputs.output_ref_amount IS NULL
      RETURNING
        outputs.address,
        outputs.groupless_address,
        outputs.tokens,
        inputs.tx_hash,
        inputs.block_hash,
        inputs.block_timestamp,
        inputs.tx_order,
        inputs.main_chain
    """
      .as[UpdateReturn]
      .flatMap(internalUpdates)
      .transactionally
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
    QuerySplitter.splitUpdates(rows = data, columnsPerRow = 8) { (data, placeholder) =>
      val query =
        s"""
            INSERT INTO transaction_per_addresses (address, groupless_address, tx_hash, block_hash, block_timestamp, tx_order, main_chain, coinbase)
            VALUES $placeholder
            ON CONFLICT ON CONSTRAINT txs_per_address_pk DO NOTHING
           """.stripMargin

      val parameters: SetParameter[Unit] =
        (_: Unit, params: PositionedParameters) =>
          data foreach {
            case (address, group, _, txHash, blockHash, timestamp, txOrder, mainChain) =>
              params >> address
              params >> group
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
      case (address, group, tokensOpt, txHash, blockHash, timestamp, txOrder, mainChain) =>
        tokensOpt match {
          case None => ArraySeq.empty
          case Some(tokens) =>
            tokens.map { token =>
              (address, group, txHash, blockHash, timestamp, txOrder, mainChain, token.id)
            }
        }
    }

    QuerySplitter.splitUpdates(rows = tokenTxPerAddresses, columnsPerRow = 8) {
      (tokenTxPerAddresses, placeholder) =>
        val query =
          s"""
              INSERT INTO token_tx_per_addresses (address, groupless_address, tx_hash, block_hash, block_timestamp, tx_order, main_chain, token)
              VALUES $placeholder
              ON CONFLICT ON CONSTRAINT token_tx_per_address_pk DO NOTHING
             """

        val parameters: SetParameter[Unit] =
          (_: Unit, params: PositionedParameters) =>
            tokenTxPerAddresses foreach {
              case (address, group, txHash, blockHash, timestamp, txOrder, mainChain, token) =>
                params >> address
                params >> group
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
