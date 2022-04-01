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

import org.alephium.explorer.Hash
import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence._
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.persistence.schema._
import org.alephium.explorer.persistence.schema.CustomGetResult._
import org.alephium.explorer.persistence.schema.CustomJdbcTypes._
import org.alephium.explorer.persistence.schema.CustomSetParameter._
import org.alephium.util.{TimeStamp, U256}

object OutputQueries {
  private val mainInputs  = InputSchema.table.filter(_.mainChain)
  private val mainOutputs = OutputSchema.table.filter(_.mainChain)

  /** Inserts outputs or ignore rows with primary key conflict */
  // scalastyle:off magic.number
  def insertOutputs(outputs: Iterable[OutputEntity]): DBActionW[Int] =
    QuerySplitter.splitUpdates(rows = outputs, columnsPerRow = 11) { (outputs, placeholder) =>
      val query =
        s"""
           |INSERT INTO outputs ("block_hash",
           |                     "tx_hash",
           |                     "block_timestamp",
           |                     "hint",
           |                     "key",
           |                     "amount",
           |                     "address",
           |                     "main_chain",
           |                     "lock_time",
           |                     "output_order",
           |                     "tx_order")
           |VALUES $placeholder
           |ON CONFLICT
           |    ON CONSTRAINT outputs_pk
           |    DO NOTHING
           |""".stripMargin

      val parameters: SetParameter[Unit] =
        (_: Unit, params: PositionedParameters) =>
          outputs foreach { output =>
            params >> output.blockHash
            params >> output.txHash
            params >> output.timestamp
            params >> output.hint
            params >> output.key
            params >> output.amount
            params >> output.address
            params >> output.mainChain
            params >> output.lockTime
            params >> output.order
            params >> output.txOrder
        }

      SQLActionBuilder(
        queryParts = query,
        unitPConv  = parameters
      ).asUpdate
    }
  // scalastyle:on magic.number

  def insertTxPerAddressFromOutputs(outputs: Seq[OutputEntity]): DBActionW[Int] = {
    if (outputs.nonEmpty) {
      val values = outputs
        .map { output =>
          s"('${output.address}','\\x${output.txHash}','\\x${output.blockHash}','${output.timestamp.millis}', ${output.txOrder},${output.mainChain})"
        }
        .mkString(",\n")
      sqlu"""
      INSERT INTO transaction_per_addresses (address, hash, block_hash, block_timestamp, tx_order, main_chain)
      VALUES #$values
      ON CONFLICT (hash, block_hash, address) DO NOTHING
    """
    } else {
      DBIOAction.successful(0)
    }
  }

  @SuppressWarnings(Array("org.wartremover.warts.PublicInference"))
  def outputsFromTxs(txHashes: Seq[Transaction.Hash]) = {
    mainOutputs
      .filter(_.txHash inSet txHashes)
      .joinLeft(mainInputs)
      .on {
        case (out, inputs) =>
          out.key === inputs.outputRefKey
      }
      .map {
        case (output, input) =>
          (output.txHash,
           output.outputOrder,
           output.hint,
           output.key,
           output.amount,
           output.address,
           output.lockTime,
           input.map(_.txHash))
      }
  }

  // format: off
  def outputsFromTxsSQL(txHashes: Seq[Transaction.Hash]):
  DBActionR[Seq[(Transaction.Hash, Int, Int, Hash, U256, Address, Option[TimeStamp], Option[Transaction.Hash])]] = {
  // format: on
    if (txHashes.nonEmpty) {
      val values = txHashes.map(hash => s"'\\x$hash'").mkString(",")
      sql"""
    SELECT outputs.tx_hash, outputs.output_order, outputs.hint, outputs.key,  outputs.amount, outputs.address, outputs.lock_time, inputs.tx_hash
    FROM outputs
    LEFT JOIN inputs ON inputs.output_ref_key = outputs.key AND inputs.main_chain = true
    WHERE outputs.tx_hash IN (#$values) AND outputs.main_chain = true
    """.as
    } else {
      DBIOAction.successful(Seq.empty)
    }
  }

  @SuppressWarnings(Array("org.wartremover.warts.PublicInference"))
  val getOutputsQuery = Compiled { (txHash: Rep[Transaction.Hash]) =>
    OutputSchema.table
      .filter(output => output.mainChain && output.txHash === txHash)
      .joinLeft(mainInputs)
      .on(_.key === _.outputRefKey)
      .sortBy(_._1.outputOrder)
      .map {
        case (output, input) =>
          (output.hint,
           output.key,
           output.amount,
           output.address,
           output.lockTime,
           input.map(_.txHash))
      }
  }

  @SuppressWarnings(Array("org.wartremover.warts.PublicInference"))
  val toApiOutput = (Output.apply _).tupled
}
