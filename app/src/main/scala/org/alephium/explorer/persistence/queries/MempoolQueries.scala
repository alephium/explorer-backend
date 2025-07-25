// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.queries

import scala.collection.immutable.ArraySeq

import slick.dbio.DBIOAction
import slick.jdbc.{PositionedParameters, SetParameter, SQLActionBuilder}
import slick.jdbc.PostgresProfile.api._

import org.alephium.api.model.{Address => ApiAddress}
import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence._
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.persistence.schema.CustomGetResult._
import org.alephium.explorer.persistence.schema.CustomSetParameter._
import org.alephium.explorer.util.SlickUtil._
import org.alephium.protocol.model.TransactionId

object MempoolQueries {

  val listHashesQuery: DBActionSR[TransactionId] = {
    sql"""
      SELECT hash
      FROM utransactions
    """.asAS[TransactionId]
  }

  def listPaginatedMempoolTransactionsQuery(
      pagination: Pagination
  ): DBActionSR[MempoolTransactionEntity] = {
    sql"""
      SELECT hash,
             chain_from,
             chain_to,
             gas_amount,
             gas_price,
             last_seen
      FROM utransactions
      ORDER BY last_seen DESC
    """
      .paginate(pagination)
      .asASE[MempoolTransactionEntity](mempoolTransactionGetResult)
  }

  def listUTXHashesByAddress(
      address: ApiAddress
  ): DBActionSR[TransactionId] = {
    sql"""
      SELECT #${distinct(address)} tx_hash
      FROM uinputs
      WHERE #${addressColumn(address)} = $address
      UNION
      SELECT #${distinct(address)} tx_hash
      FROM uoutputs
      WHERE #${addressColumn(address)} = $address
    """.asAS[TransactionId]
  }

  def utxsFromTxs(hashes: ArraySeq[TransactionId]): DBActionSR[MempoolTransactionEntity] = {
    if (hashes.nonEmpty) {
      val params = paramPlaceholder(1, hashes.size)

      val query =
        s"""
           SELECT hash,
                  chain_from,
                  chain_to,
                  gas_amount,
                  gas_price,
                  last_seen
           FROM utransactions
           WHERE hash IN $params
           ORDER BY last_seen DESC
           """

      val parameters: SetParameter[Unit] =
        (_: Unit, params: PositionedParameters) =>
          hashes foreach { txnHash =>
            params >> txnHash
          }

      SQLActionBuilder(
        sql = query,
        setParameter = parameters
      ).asASE[MempoolTransactionEntity](mempoolTransactionGetResult)
    } else {
      DBIOAction.successful(ArraySeq.empty)
    }
  }

  def uoutputsFromTxs(hashes: ArraySeq[TransactionId]): DBActionSR[UOutputEntity] = {
    if (hashes.nonEmpty) {
      val params = paramPlaceholder(1, hashes.size)

      val query =
        s"""
           SELECT tx_hash,
                  hint,
                  key,
                  amount,
                  address,
                  groupless_address,
                  tokens,
                  lock_time,
                  message,
                  uoutput_order
           FROM uoutputs
           WHERE tx_hash IN $params
           """

      val parameters: SetParameter[Unit] =
        (_: Unit, params: PositionedParameters) =>
          hashes foreach { txnHash =>
            params >> txnHash
          }

      SQLActionBuilder(
        sql = query,
        setParameter = parameters
      ).asASE[UOutputEntity](uoutputGetResult)
    } else {
      DBIOAction.successful(ArraySeq.empty)
    }
  }

  def uinputsFromTxs(hashes: ArraySeq[TransactionId]): DBActionSR[UInputEntity] = {
    if (hashes.nonEmpty) {
      val params = paramPlaceholder(1, hashes.size)

      val query =
        s"""
           SELECT tx_hash,
                  hint,
                  output_ref_key,
                  unlock_script,
                  address,
                  groupless_address,
                  uinput_order
           FROM uinputs
           WHERE tx_hash IN $params
           """

      val parameters: SetParameter[Unit] =
        (_: Unit, params: PositionedParameters) =>
          hashes foreach { txnHash =>
            params >> txnHash
          }

      SQLActionBuilder(
        sql = query,
        setParameter = parameters
      ).asASE[UInputEntity](uinputGetResult)
    } else {
      DBIOAction.successful(ArraySeq.empty)
    }
  }

  def utxFromTxHash(hash: TransactionId): DBActionSR[MempoolTransactionEntity] = {
    sql"""
         SELECT hash,
                chain_from,
                chain_to,
                gas_amount,
                gas_price,
                last_seen
         FROM utransactions
         WHERE hash = $hash
         """.asASE[MempoolTransactionEntity](mempoolTransactionGetResult)
  }

  def uoutputsFromTx(hash: TransactionId): DBActionSR[UOutputEntity] = {
    sql"""
         SELECT tx_hash,
                hint,
                key,
                amount,
                address,
                groupless_address,
                tokens,
                lock_time,
                message,
                uoutput_order
         FROM uoutputs
         WHERE tx_hash = $hash
         ORDER BY uoutput_order
         """.asASE[UOutputEntity](uoutputGetResult)
  }

  def uinputsFromTx(hash: TransactionId): DBActionSR[UInputEntity] = {
    sql"""
         SELECT tx_hash,
                hint,
                output_ref_key,
                unlock_script,
                address,
                groupless_address,
                uinput_order
         FROM uinputs
         WHERE tx_hash = $hash
         ORDER BY uinput_order
         """.asASE[UInputEntity](uinputGetResult)
  }
}
