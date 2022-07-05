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

import scala.concurrent.ExecutionContext

import slick.jdbc.{PositionedParameters, SetParameter, SQLActionBuilder}
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence._
import org.alephium.explorer.persistence.schema.CustomGetResult._
import org.alephium.explorer.persistence.schema.CustomSetParameter._
import org.alephium.util._

object InputUpdateQueries {

  def updateInputs()(implicit ec: ExecutionContext): DBActionWT[Int] = {
    sql"""
      UPDATE inputs
      SET
        output_ref_address = outputs.address,
        output_ref_amount = outputs.amount
      FROM outputs
      WHERE inputs.output_ref_key = outputs.key
      AND inputs.output_ref_address IS NULL
      RETURNING outputs.address, inputs.tx_hash, inputs.block_hash, inputs.block_timestamp, inputs.tx_order, inputs.main_chain
    """
      .as[(Address, Transaction.Hash, BlockEntry.Hash, TimeStamp, Int, Boolean)]
      .flatMap(updateTransactionPerAddresses)
      .transactionally
  }

  private def updateTransactionPerAddresses(
      data: Vector[(Address, Transaction.Hash, BlockEntry.Hash, TimeStamp, Int, Boolean)])
    : DBActionWT[Int] = {
    QuerySplitter.splitUpdates(rows = data, columnsPerRow = 6) { (data, placeholder) =>
      val query =
        s"""
           | INSERT INTO transaction_per_addresses (address, hash, block_hash, block_timestamp, tx_order, main_chain)
           | VALUES $placeholder
           | ON CONFLICT ON CONSTRAINT txs_per_address_pk DO NOTHING
           |""".stripMargin

      val parameters: SetParameter[Unit] =
        (_: Unit, params: PositionedParameters) =>
          data foreach {
            case (address, txHash, blockHash, timestamp, txOrder, mainChain) =>
              params >> address
              params >> txHash
              params >> blockHash
              params >> timestamp
              params >> txOrder
              params >> mainChain
        }

      SQLActionBuilder(
        queryParts = query,
        unitPConv  = parameters
      ).asUpdate
    }
  }
}
