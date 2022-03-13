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
import org.alephium.explorer.persistence.schema.CustomSetParameter._
import org.alephium.util.U256

object InputQueries extends CustomTypes {

  private val mainInputs  = InputSchema.table.filter(_.mainChain)
  private val mainOutputs = OutputSchema.table.filter(_.mainChain)

  /** Inserts inputs or ignore rows with primary key conflict */
  def insertInputs(inputs: Iterable[InputEntity]): DBActionW[Int] =
    QueryUtil.splitUpdates(rows = inputs, queryRowParams = 9) { (inputs, placeholder) =>
      val query =
        s"""
           |INSERT INTO inputs ("block_hash",
           |                    "tx_hash",
           |                    "timestamp",
           |                    "hint",
           |                    "output_ref_key",
           |                    "unlock_script",
           |                    "main_chain",
           |                    "order",
           |                    "tx_index")
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
            params >> input.txIndex
        }

      SQLActionBuilder(
        queryParts = query,
        unitPConv  = parameters
      ).asUpdate
    }

  def insertTxPerAddressFromInputs(inputs: Seq[InputEntity], outputs: Seq[OutputEntity])(
      implicit ec: ExecutionContext): DBActionW[Seq[InputEntity]] = {

    val inputsAdressOption = inputs.map(in => (in.address, in))

    val outputsByAddress = outputs.groupBy(out => (out.address, out.txHash))

    val assets = inputsAdressOption
      .collect {
        case (Some(address), input) if outputsByAddress.get((address, input.txHash)).isEmpty =>
          (address, input)
      }
      .distinctBy { case (address, input) => (address, input.txHash) }

    val others = inputsAdressOption.collect {
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
    } yield inputsToUpdate
  }

  def insertTxPerAddressFromInput(input: InputEntity): DBActionW[Int] = {
    sqlu"""
      INSERT INTO transaction_per_addresses (address, hash, block_hash, timestamp, tx_index, main_chain)
      (SELECT address, ${input.txHash}, ${input.blockHash}, ${input.timestamp}, ${input.txIndex}, main_chain FROM outputs WHERE key = ${input.outputRefKey})
      ON CONFLICT (hash, block_hash, address) DO NOTHING
    """
  }

  def insertInputWithAddress(inputs: Seq[(Address, InputEntity)]): DBActionW[Int] = {
    if (inputs.nonEmpty) {
      val values = inputs
        .map {
          case (address, input) =>
            s"('${address}', '\\x${input.txHash}', '\\x${input.blockHash}', '${input.timestamp.millis}', ${input.txIndex}, ${input.mainChain}) "
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
  def inputsFromTxs(txHashes: Seq[Transaction.Hash]) = {
    mainInputs
      .filter(_.txHash inSet txHashes)
      .join(mainOutputs)
      .on {
        case (input, outputs) =>
          input.outputRefKey === outputs.key
      }
      .map {
        case (input, output) =>
          (input.txHash,
           input.order,
           input.hint,
           input.outputRefKey,
           input.unlockScript,
           output.txHash,
           output.address,
           output.amount)
      }
  }

  // format: off
  def inputsFromTxsSQL(txHashes: Seq[Transaction.Hash]):
    DBActionSR[(Transaction.Hash, Int, Int, Hash, Option[String], Transaction.Hash, Address, U256)] = {
  // format: on
    val values = txHashes.map(hash => s"'\\x$hash'").mkString(",")
    sql"""
    SELECT inputs.tx_hash, inputs.order, inputs.hint, inputs.output_ref_key, inputs.unlock_script, outputs.tx_hash, outputs.address, outputs.amount
    FROM inputs
    JOIN outputs ON inputs.output_ref_key = outputs.key AND outputs.main_chain = true
    WHERE inputs.tx_hash IN (#$values) AND inputs.main_chain = true
    """.as
  }

  @SuppressWarnings(Array("org.wartremover.warts.PublicInference"))
  val getInputsQuery = Compiled { (txHash: Rep[Transaction.Hash]) =>
    mainInputs
      .filter(_.txHash === txHash)
      .join(mainOutputs)
      .on(_.outputRefKey === _.key)
      .sortBy(_._1.order)
      .map {
        case (input, output) =>
          (input.hint,
           input.outputRefKey,
           input.unlockScript,
           output.txHash,
           output.address,
           output.amount)
      }
  }

  @SuppressWarnings(Array("org.wartremover.warts.PublicInference"))
  val toApiInput = {
    (hint: Int,
     key: Hash,
     unlockScript: Option[String],
     txHash: Transaction.Hash,
     address: Address,
     amount: U256) =>
      Input(Output.Ref(hint, key), unlockScript, txHash, address, amount)
  }.tupled
}