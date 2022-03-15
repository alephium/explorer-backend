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
import org.alephium.explorer.persistence.schema.CustomSetParameter._
import org.alephium.explorer.persistence.schema.InputSchema._
import org.alephium.explorer.persistence.schema.OutputSchema._
import org.alephium.util.{TimeStamp, U256}

object OutputQueries extends CustomTypes {
  private val mainInputs  = inputsTable.filter(_.mainChain)
  private val mainOutputs = outputsTable.filter(_.mainChain)

  /** Inserts outputs or ignore rows with primary key conflict */
  // scalastyle:off magic.number
  def insertOutputs(outputs: Iterable[OutputEntity]): DBActionW[Int] =
    QueryUtil.splitUpdates(rows = outputs, queryRowParams = 11) { (outputs, placeholder) =>
      val query =
        s"""
           |INSERT INTO outputs ("block_hash",
           |                     "tx_hash",
           |                     "timestamp",
           |                     "hint",
           |                     "key",
           |                     "amount",
           |                     "address",
           |                     "main_chain",
           |                     "lock_time",
           |                     "order",
           |                     "tx_index")
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
            params >> output.txIndex
        }

      SQLActionBuilder(
        queryParts = query,
        unitPConv  = parameters
      ).asUpdate
    }

  def insertTxPerAddressFromOutputs(outputs: Seq[OutputEntity]): DBActionW[Int] = {
    if (outputs.nonEmpty) {
      val values = outputs
        .map { output =>
          s"('${output.address}','\\x${output.txHash}','\\x${output.blockHash}','${output.timestamp.millis}', ${output.txIndex},${output.mainChain})"
        }
        .mkString(",\n")
      sqlu"""
      INSERT INTO transaction_per_addresses (address, hash, block_hash, timestamp, tx_index, main_chain)
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
           output.order,
           output.hint,
           output.key,
           output.amount,
           output.address,
           output.lockTime,
           input.map(_.txHash))
      }
  }

  def outputsFromTxsSQL(
      txHashes: Seq[Transaction.Hash]
  ): DBActionSR[(Transaction.Hash,
                 Int,
                 Int,
                 Hash,
                 U256,
                 Address,
                 Option[TimeStamp],
                 Option[Transaction.Hash])] = {
    val values = txHashes.map(hash => s"'\\x$hash'").mkString(",")
    sql"""
    SELECT outputs.tx_hash, outputs.order, outputs.hint, outputs.key,  outputs.amount, outputs.address, outputs.lock_time, inputs.tx_hash
    FROM outputs
    LEFT JOIN inputs ON inputs.output_ref_key = outputs.key AND inputs.main_chain = true
    WHERE outputs.tx_hash IN (#$values) AND outputs.main_chain = true
    """.as
  }

  @SuppressWarnings(Array("org.wartremover.warts.PublicInference"))
  val getOutputsQuery = Compiled { (txHash: Rep[Transaction.Hash]) =>
    outputsTable
      .filter(output => output.mainChain && output.txHash === txHash)
      .joinLeft(mainInputs)
      .on(_.key === _.outputRefKey)
      .sortBy(_._1.order)
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
