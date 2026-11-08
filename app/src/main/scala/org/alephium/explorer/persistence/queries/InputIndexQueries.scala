// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.queries

import scala.collection.immutable.ArraySeq

import slick.dbio.DBIOAction
import slick.jdbc.{PositionedParameters, SetParameter, SQLActionBuilder}

import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence._
import org.alephium.explorer.persistence.model.{GrouplessAddress, InputEntity}
import org.alephium.explorer.persistence.schema.CustomSetParameter._
import org.alephium.protocol.model.{Address, BlockHash, TokenId, TransactionId}
import org.alephium.util._

object InputIndexQueries {

  private type UpdateReturn =
    (
        Address,
        Option[GrouplessAddress],
        Option[ArraySeq[Token]],
        TransactionId,
        BlockHash,
        TimeStamp,
        Int,
        Boolean,
        Option[Boolean]
    )

  def insert(inputs: Iterable[InputEntity]): DBActionW[Unit] = {
    val data = inputs.iterator
      .filter(_.outputRefAmount.isDefined)
      .flatMap { input =>
        input.outputRefAddress.map { address =>
          (
            address,
            input.outputRefGrouplessAddress,
            input.outputRefTokens,
            input.txHash,
            input.blockHash,
            input.timestamp,
            input.txOrder,
            input.mainChain,
            input.conflicted
          )
        }
      }
      .toVector
    DBIOAction.seq(updateTransactionPerAddresses(data), updateTokenTxPerAddresses(data))
  }

  // format: off
  private def updateTransactionPerAddresses(
      data: Vector[UpdateReturn])
    : DBActionW[Int] = {
  // format: on
    QuerySplitter.splitUpdates(rows = data, columnsPerRow = 9) { (data, placeholder) =>
      val query =
        s"""
            INSERT INTO transaction_per_addresses (address, groupless_address, tx_hash, block_hash, block_timestamp, tx_order, main_chain, conflicted, coinbase)
            VALUES $placeholder
            ON CONFLICT ON CONSTRAINT txs_per_address_pk DO NOTHING
           """.stripMargin

      val parameters: SetParameter[Unit] =
        (_: Unit, params: PositionedParameters) =>
          data foreach {
            case (
                  address,
                  group,
                  _,
                  txHash,
                  blockHash,
                  timestamp,
                  txOrder,
                  mainChain,
                  conflicted
                ) =>
              params >> address
              params >> group
              params >> txHash
              params >> blockHash
              params >> timestamp
              params >> txOrder
              params >> mainChain
              params >> conflicted
              params >> false
          }

      SQLActionBuilder(
        sql = query,
        setParameter = parameters
      ).asUpdate
    }
  }

  private type TokenRow = (
      Address,
      Option[GrouplessAddress],
      TransactionId,
      BlockHash,
      TimeStamp,
      Int,
      Boolean,
      Option[Boolean],
      TokenId
  )

  private def tokenRows(data: Vector[UpdateReturn]): Vector[TokenRow] =
    data.flatMap {
      case (
            address,
            group,
            tokensOpt,
            txHash,
            blockHash,
            timestamp,
            txOrder,
            mainChain,
            conflicted
          ) =>
        tokensOpt match {
          case None => ArraySeq.empty
          case Some(tokens) =>
            tokens.map { token =>
              (
                address,
                group,
                txHash,
                blockHash,
                timestamp,
                txOrder,
                mainChain,
                conflicted,
                token.id
              )
            }
        }
    }

  // format: off
  private def updateTokenTxPerAddresses(
      data: Vector[UpdateReturn])
    : DBActionW[Int] = {
  // format: on
    val tokenTxPerAddresses = tokenRows(data)

    QuerySplitter.splitUpdates(rows = tokenTxPerAddresses, columnsPerRow = 9) {
      (tokenTxPerAddresses, placeholder) =>
        val query =
          s"""
              INSERT INTO token_tx_per_addresses (address, groupless_address, tx_hash, block_hash, block_timestamp, tx_order, main_chain, conflicted, token)
              VALUES $placeholder
              ON CONFLICT ON CONSTRAINT token_tx_per_address_pk DO NOTHING
             """

        val parameters: SetParameter[Unit] =
          (_: Unit, params: PositionedParameters) =>
            tokenTxPerAddresses foreach {
              case (
                    address,
                    group,
                    txHash,
                    blockHash,
                    timestamp,
                    txOrder,
                    mainChain,
                    conflicted,
                    token
                  ) =>
                params >> address
                params >> group
                params >> txHash
                params >> blockHash
                params >> timestamp
                params >> txOrder
                params >> mainChain
                params >> conflicted
                params >> token
            }

        SQLActionBuilder(
          sql = query,
          setParameter = parameters
        ).asUpdate
    }
  }
}
