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
import org.alephium.util.U256

object InputQueries {

  private val mainInputs  = InputSchema.table.filter(_.mainChain)
  private val mainOutputs = OutputSchema.table.filter(_.mainChain)

  /** Inserts inputs or ignore rows with primary key conflict */
  def insertInputs(inputs: Iterable[InputEntity]): DBActionW[Int] =
    QuerySplitter.splitUpdates(rows = inputs, columnsPerRow = 9) { (inputs, placeholder) =>
      val query =
        s"""
           |INSERT INTO inputs ("block_hash",
           |                    "tx_hash",
           |                    "block_timestamp",
           |                    "hint",
           |                    "output_ref_key",
           |                    "unlock_script",
           |                    "main_chain",
           |                    "input_order",
           |                    "tx_order")
           |VALUES $placeholder
           |ON CONFLICT
           |    ON CONSTRAINT inputs_pk
           |    DO NOTHING
           |""".stripMargin

      val parameters: SetParameter[Unit] =
        (_: Unit, params: PositionedParameters) =>
          inputs foreach { input =>
            params >> input.blockHash
            params >> input.txHash
            params >> input.timestamp
            params >> input.hint
            params >> input.outputRefKey
            params >> input.unlockScript
            params >> input.mainChain
            params >> input.order
            params >> input.txOrder
        }

      SQLActionBuilder(
        queryParts = query,
        unitPConv  = parameters
      ).asUpdate
    }

  def insertTxPerAddressFromInputs(inputs: Seq[InputEntity], outputs: Seq[OutputEntity])(
      implicit ec: ExecutionContext): DBActionW[Seq[InputEntity]] = {

    val inputsAddressOption = inputs.map(in => (in.address, in))

    val outputsByAddress = outputs.groupBy(out => (out.address, out.txHash))

    val assets = inputsAddressOption
      .collect {
        case (Some(address), input) if !outputsByAddress.contains((address, input.txHash)) =>
          (address, input)
      }
      .distinctBy { case (address, input) => (address, input.txHash) }

    val others = inputsAddressOption.collect {
      case (None, input) => input
    }

    for {
      _ <- insertInputWithAddress(assets)
      inputsToUpdate <- DBIOAction
        .sequence(others.map { input =>
          insertTxPerAddressFromInput(input).map { i =>
            if (i != 1) {
              Seq(input)
            } else {
              Seq.empty
            }
          }
        })
        .map(_.flatten)
      _ <- insertTokenPerAddressFromInputs(inputs)
    } yield (inputsToUpdate).distinct
  }

  def insertTxPerAddressFromInput(input: InputEntity): DBActionW[Int] = {
    sqlu"""
      INSERT INTO transaction_per_addresses (address, tx_hash, block_hash, block_timestamp, tx_order, main_chain)
      (SELECT address, ${input.txHash}, ${input.blockHash}, ${input.timestamp}, ${input.txOrder}, main_chain FROM outputs WHERE key = ${input.outputRefKey})
      ON CONFLICT (tx_hash, block_hash, address) DO NOTHING
    """
  }

  def insertInputWithAddress(inputs: Iterable[(Address, InputEntity)]): DBActionW[Int] = {
    QuerySplitter
      .splitUpdates(rows = inputs, columnsPerRow = 6) { (inputs, placeholder) =>
        val query =
          s"""
          INSERT INTO transaction_per_addresses (address, tx_hash, block_hash, block_timestamp, tx_order, main_chain)
          VALUES $placeholder
          ON CONFLICT (tx_hash, block_hash, address) DO NOTHING
        """

        val parameters: SetParameter[Unit] =
          (_: Unit, params: PositionedParameters) =>
            inputs foreach {
              case (address, input) =>
                params >> address
                params >> input.txHash
                params >> input.blockHash
                params >> input.timestamp
                params >> input.txOrder
                params >> input.mainChain
          }

        SQLActionBuilder(
          queryParts = query,
          unitPConv  = parameters
        ).asUpdate
      }
  }

  def insertTokenPerAddressFromInputs(inputs: Iterable[InputEntity])(
      implicit ec: ExecutionContext): DBActionW[Unit] = {
    DBIOAction
      .sequence(inputs.map { input =>
        insertTokenPerAddressFromInput(input)
      })
      .map(_ => ())
  }

  def insertTokenPerAddressFromInput(input: InputEntity): DBActionW[Int] = {
    sqlu"""
          INSERT INTO token_tx_per_addresses (address, tx_hash, block_hash, block_timestamp, tx_order, main_chain, token)
          SELECT address, tx_hash, block_hash, block_timestamp, tx_order, main_chain, token
          FROM token_outputs
          WHERE key = ${input.outputRefKey}
          ON CONFLICT (tx_hash, block_hash, address, token) DO NOTHING
        """
  }

  // format: off
  def inputsFromTxsSQL(txHashes: Seq[Transaction.Hash]):
    DBActionR[Seq[(Transaction.Hash, Int, Int, Hash, Option[String], Transaction.Hash, Address, U256, Option[Seq[Token]])]] = {
  // format: on
    if (txHashes.nonEmpty) {
      val values = txHashes.map(hash => s"'\\x$hash'").mkString(",")
      sql"""
    SELECT
      inputs.tx_hash,
      inputs.input_order,
      inputs.hint,
      inputs.output_ref_key,
      inputs.unlock_script,
      outputs.tx_hash,
      outputs.address,
      outputs.amount,
      outputs.tokens
    FROM inputs
    JOIN outputs ON inputs.output_ref_key = outputs.key AND outputs.main_chain = true
    WHERE inputs.tx_hash IN (#$values) AND inputs.main_chain = true
    """.as
    } else {
      DBIOAction.successful(Seq.empty)
    }
  }

  @SuppressWarnings(Array("org.wartremover.warts.PublicInference"))
  val getInputsQuery = Compiled { (txHash: Rep[Transaction.Hash]) =>
    mainInputs
      .filter(_.txHash === txHash)
      .join(mainOutputs)
      .on(_.outputRefKey === _.key)
      .sortBy(_._1.inputOrder)
      .map {
        case (input, output) =>
          (input.hint,
           input.outputRefKey,
           input.unlockScript,
           output.txHash,
           output.address,
           output.amount,
           output.tokens)
      }
  }

  @SuppressWarnings(Array("org.wartremover.warts.PublicInference"))
  val toApiInput = {
    (hint: Int,
     key: Hash,
     unlockScript: Option[String],
     txHash: Transaction.Hash,
     address: Address,
     amount: U256,
     tokens: Option[Seq[Token]]) =>
      Input(OutputRef(hint, key), unlockScript, txHash, address, amount, tokens)
  }.tupled
}
