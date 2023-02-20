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

import slick.dbio.DBIOAction
import slick.jdbc.{PositionedParameters, SetParameter, SQLActionBuilder}
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence._
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.persistence.schema.CustomGetResult._
import org.alephium.explorer.persistence.schema.CustomSetParameter._
import org.alephium.explorer.util.SlickUtil._
import org.alephium.protocol.model.{Address, TransactionId}

object UnconfirmedTransactionQueries {

  val listHashesQuery: DBActionSR[TransactionId] = {
    sql"""
      SELECT hash
      FROM utransactions
    """.asAS[TransactionId]
  }

  def listPaginatedUnconfirmedTransactionsQuery(
      pagination: Pagination): DBActionSR[UnconfirmedTxEntity] = {
    sql"""
      SELECT hash,
             chain_from,
             chain_to,
             gas_amount,
             gas_price,
             last_seen
      FROM utransactions
      ORDER BY last_seen DESC
      #${pagination.query}
    """.asASE[UnconfirmedTxEntity](unconfirmedTransactionGetResult)
  }

  def listUTXHashesByAddress(address: Address): DBActionSR[TransactionId] = {
    sql"""
      SELECT DISTINCT tx_hash
      FROM uinputs
      WHERE address = $address
    """.asAS[TransactionId]
  }

  def utxsFromTxs(hashes: ArraySeq[TransactionId]): DBActionSR[UnconfirmedTxEntity] = {
    if (hashes.nonEmpty) {
      val params = paramPlaceholder(1, hashes.size)

      val query =
        s"""
           |SELECT hash,
           |       chain_from,
           |       chain_to,
           |       gas_amount,
           |       gas_price,
           |       last_seen
           |FROM utransactions
           |WHERE hash IN $params
           |ORDER BY last_seen DESC
           |""".stripMargin

      val parameters: SetParameter[Unit] =
        (_: Unit, params: PositionedParameters) =>
          hashes foreach { txnHash =>
            params >> txnHash
        }

      SQLActionBuilder(
        queryParts = query,
        unitPConv  = parameters
      ).asASE[UnconfirmedTxEntity](unconfirmedTransactionGetResult)
    } else {
      DBIOAction.successful(ArraySeq.empty)
    }
  }

  def uoutputsFromTxs(hashes: ArraySeq[TransactionId]): DBActionSR[UOutputEntity] = {
    if (hashes.nonEmpty) {
      val params = paramPlaceholder(1, hashes.size)

      val query =
        s"""
           |SELECT tx_hash,
           |       hint,
           |       key,
           |       amount,
           |       address,
           |       tokens,
           |       lock_time,
           |       message,
           |       uoutput_order
           |FROM uoutputs
           |WHERE tx_hash IN $params
           |""".stripMargin

      val parameters: SetParameter[Unit] =
        (_: Unit, params: PositionedParameters) =>
          hashes foreach { txnHash =>
            params >> txnHash
        }

      SQLActionBuilder(
        queryParts = query,
        unitPConv  = parameters
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
           |SELECT tx_hash,
           |       hint,
           |       output_ref_key,
           |       unlock_script,
           |       address,
           |       uinput_order
           |FROM uinputs
           |WHERE tx_hash IN $params
           |""".stripMargin

      val parameters: SetParameter[Unit] =
        (_: Unit, params: PositionedParameters) =>
          hashes foreach { txnHash =>
            params >> txnHash
        }

      SQLActionBuilder(
        queryParts = query,
        unitPConv  = parameters
      ).asASE[UInputEntity](uinputGetResult)
    } else {
      DBIOAction.successful(ArraySeq.empty)
    }
  }

  def utxFromTxHash(hash: TransactionId): DBActionSR[UnconfirmedTxEntity] = {
    sql"""
           |SELECT hash,
           |       chain_from,
           |       chain_to,
           |       gas_amount,
           |       gas_price,
           |       last_seen
           |FROM utransactions
           |WHERE hash = $hash
           |""".stripMargin.asASE[UnconfirmedTxEntity](unconfirmedTransactionGetResult)
  }

  def uoutputsFromTx(hash: TransactionId): DBActionSR[UOutputEntity] = {
    sql"""
           |SELECT tx_hash,
           |       hint,
           |       key,
           |       amount,
           |       address,
           |       tokens,
           |       lock_time,
           |       message,
           |       uoutput_order
           |FROM uoutputs
           |WHERE tx_hash = $hash
           |ORDER BY uoutput_order
           |""".stripMargin.asASE[UOutputEntity](uoutputGetResult)
  }

  def uinputsFromTx(hash: TransactionId): DBActionSR[UInputEntity] = {
    sql"""
           |SELECT tx_hash,
           |       hint,
           |       output_ref_key,
           |       unlock_script,
           |       address,
           |       uinput_order
           |FROM uinputs
           |WHERE tx_hash = $hash
           |ORDER BY uinput_order
           |""".stripMargin.asASE[UInputEntity](uinputGetResult)
  }
}
