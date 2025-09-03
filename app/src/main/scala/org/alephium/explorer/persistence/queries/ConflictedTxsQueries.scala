// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.queries

import scala.collection.immutable.ArraySeq

import com.typesafe.scalalogging.StrictLogging
import slick.jdbc.{PositionedParameters, SetParameter, SQLActionBuilder}
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.persistence._
import org.alephium.explorer.persistence.schema.CustomGetResult._
import org.alephium.explorer.persistence.schema.CustomSetParameter._
import org.alephium.explorer.util.SlickUtil._
import org.alephium.protocol.model.{BlockHash, TransactionId}
import org.alephium.util.TimeStamp

@SuppressWarnings(Array("org.wartremover.warts.IterableOps"))
case object ConflictedTxsQueries extends StrictLogging {

  def updateConflictStatus(
      txHash: TransactionId,
      blockHash: BlockHash,
      conflicted: Boolean
  ): DBActionRWT[Int] = {

    val query =
      s"""
         BEGIN;
         UPDATE transactions              SET conflicted = ? WHERE hash    = ? AND block_hash = ?;
         UPDATE outputs                   SET conflicted = ? WHERE tx_hash = ? AND block_hash = ?;
         UPDATE inputs                    SET conflicted = ? WHERE tx_hash = ? AND block_hash = ?;
         UPDATE transaction_per_addresses SET conflicted = ? WHERE tx_hash = ? AND block_hash = ?;
         UPDATE transaction_per_token     SET conflicted = ? WHERE tx_hash = ? AND block_hash = ?;
         UPDATE token_tx_per_addresses    SET conflicted = ? WHERE tx_hash = ? AND block_hash = ?;
         UPDATE token_outputs             SET conflicted = ? WHERE tx_hash = ? AND block_hash = ?;
         COMMIT;
      """

    val parameters: SetParameter[Unit] =
      (_: Unit, params: PositionedParameters) =>
        (1 to 7) foreach { _ =>
          params >> Some(conflicted)
          params >> txHash
          params >> blockHash
        }

    SQLActionBuilder(
      sql = query,
      setParameter = parameters
    ).asUpdate

  }

  def updateBlockConflictTxs(
      blockHash: BlockHash,
      conflictedTxs: Option[ArraySeq[TransactionId]]
  ): DBActionRWT[Int] = {
    sql"""
      UPDATE block_headers
      SET conflicted_txs = $conflictedTxs
      WHERE hash = $blockHash;
    """.asUpdate
  }

  def findConflictedTxs(
      timestamp: TimeStamp
  ): DBActionSR[(TransactionId, BlockHash, Option[Boolean])] =
    sql"""
     SELECT DISTINCT ON (i.tx_hash) i.tx_hash, i.block_hash, i.conflicted
      FROM inputs i
      WHERE i.block_timestamp > $timestamp
        AND i.main_chain = true
        AND i.output_ref_key IN (
          SELECT output_ref_key
          FROM inputs
          WHERE block_timestamp > $timestamp
            AND main_chain = true
          GROUP BY output_ref_key
          HAVING COUNT(DISTINCT block_hash) > 1
        )
      """.asAS[(TransactionId, BlockHash, Option[Boolean])]

  def findTxsUsingConflictedTxs(
      timestamp: TimeStamp
  ): DBActionSR[(TransactionId, BlockHash, Option[Boolean])] =
    sql"""
      SELECT DISTINCT ON (i.tx_hash) i.tx_hash, i.block_hash, i.conflicted
      FROM inputs i
      JOIN inputs ref ON i.output_ref_tx_hash = ref.tx_hash
      WHERE i.block_timestamp > $timestamp
        AND i.main_chain = true
        AND ref.block_timestamp > $timestamp
        AND ref.main_chain = true
        AND ref.conflicted = true
      """.asAS[(TransactionId, BlockHash, Option[Boolean])]

  // Find inputs that were conflicted, but due to a reorg aren't anymore.
  def findReorgedConflictedTxs(
      timestamp: TimeStamp
  ): DBActionSR[(TransactionId, BlockHash)] = {
    sql"""
        SELECT DISTINCT ON (i.tx_hash) i.tx_hash,i.block_hash
        FROM inputs i
        WHERE i.block_timestamp > $timestamp
          AND i.conflicted IS NOT NULL
          AND i.output_ref_key IN (
            SELECT output_ref_key
            FROM inputs
            WHERE block_timestamp > $timestamp
              AND conflicted IS NOT NULL
            GROUP BY output_ref_key
            HAVING
              BOOL_OR(main_chain)         -- at least one TRUE
              AND BOOL_OR(NOT main_chain) -- at least one FALSE
          )
      """.asAS[(TransactionId, BlockHash)]
  }
}
