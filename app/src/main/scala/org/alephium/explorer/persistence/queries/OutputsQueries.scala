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

import com.typesafe.scalalogging.StrictLogging
import slick.basic.DatabaseConfig
import slick.dbio.DBIOAction
import slick.jdbc.JdbcProfile

import org.alephium.explorer.Hash
import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence._
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.persistence.schema._
import org.alephium.util.{TimeStamp, U256}

trait OutputsQueries extends OutputSchema with InputSchema with CustomTypes with StrictLogging {

  implicit def executionContext: ExecutionContext
  val config: DatabaseConfig[JdbcProfile]
  import config.profile.api._

  private val mainInputs  = inputsTable.filter(_.mainChain)
  private val mainOutputs = outputsTable.filter(_.mainChain)

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

  val toApiOutput = (Output.apply _).tupled
}
