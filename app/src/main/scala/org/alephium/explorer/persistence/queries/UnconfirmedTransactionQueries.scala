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

import slick.dbio.DBIOAction
import slick.jdbc.{PositionedParameters, SetParameter, SQLActionBuilder}
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence._
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.persistence.schema.CustomGetResult._
import org.alephium.explorer.persistence.schema.CustomSetParameter._
import org.alephium.explorer.util.SlickUtil._

object UnconfirmedTransactionQueries {

  def listUnconfirmedTransactionsQuery(pagination: Pagination): DBActionSR[UnconfirmedTxEntity] = {
    val offset = pagination.offset.toLong
    val limit  = pagination.limit.toLong
    val toDrop = offset * limit
    sql"""
      SELECT hash,
             chain_from,
             chain_to,
             gas_amount,
             gas_price,
             last_seen
      FROM utransactions
      ORDER BY last_seen DESC
      LIMIT $limit
      OFFSET $toDrop
    """.as[UnconfirmedTxEntity](unconfirmedTransactionGetResult)
  }

  def listUTXHashesByAddress(address: Address): DBActionSR[Transaction.Hash] = {
    sql"""
      SELECT DISTINCT tx_hash
      FROM uinputs
      WHERE address = $address
    """.as[Transaction.Hash]
  }

  def utxsFromTxs(hashes: Seq[Transaction.Hash]): DBActionR[Seq[UnconfirmedTxEntity]] = {
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
      ).as[UnconfirmedTxEntity](unconfirmedTransactionGetResult)
    } else {
      DBIOAction.successful(Seq.empty)
    }
  }

  def uoutputsFromTxs(hashes: Seq[Transaction.Hash]): DBActionR[Seq[UOutputEntity]] = {
    if (hashes.nonEmpty) {
      val params = paramPlaceholder(1, hashes.size)

      val query =
        s"""
           |SELECT tx_hash,
           |       amount,
           |       address,
           |       lock_time,
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
      ).as[UOutputEntity](uoutputGetResult)
    } else {
      DBIOAction.successful(Seq.empty)
    }
  }

  def uinputsFromTxs(hashes: Seq[Transaction.Hash]): DBActionR[Seq[UInputEntity]] = {
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
      ).as[UInputEntity](uinputGetResult)
    } else {
      DBIOAction.successful(Seq.empty)
    }
  }

  def utxFromTxHash(hash: Transaction.Hash): DBActionR[Seq[UnconfirmedTxEntity]] = {
    sql"""
           |SELECT hash,
           |       chain_from,
           |       chain_to,
           |       gas_amount,
           |       gas_price,
           |       last_seen
           |FROM utransactions
           |WHERE hash = $hash
           |""".stripMargin.as[UnconfirmedTxEntity](unconfirmedTransactionGetResult)
  }

  def uoutputsFromTx(hash: Transaction.Hash): DBActionR[Seq[UOutputEntity]] = {
    sql"""
           |SELECT tx_hash,
           |       amount,
           |       address,
           |       lock_time,
           |       uoutput_order
           |FROM uoutputs
           |WHERE tx_hash = $hash
           |ORDER BY uoutput_order
           |""".stripMargin.as[UOutputEntity](uoutputGetResult)
  }

  def uinputsFromTx(hash: Transaction.Hash): DBActionR[Seq[UInputEntity]] = {
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
           |""".stripMargin.as[UInputEntity](uinputGetResult)
  }
}
