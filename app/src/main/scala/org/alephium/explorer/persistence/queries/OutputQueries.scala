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

import akka.util.ByteString
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
  private val mainInputs = InputSchema.table.filter(_.mainChain)

  def insertOutputs(outputs: Iterable[OutputEntity]): DBActionRWT[Unit] =
    DBIOAction
      .seq(insertBasicOutputs(outputs),
           insertTxPerAddressFromOutputs(outputs),
           insertTokensFromOutputs(outputs))
      .transactionally

  /** Inserts outputs or ignore rows with primary key conflict */
  // scalastyle:off magic.number
  private def insertBasicOutputs(outputs: Iterable[OutputEntity]): DBActionW[Int] =
    QuerySplitter
      .splitUpdates(rows = outputs, columnsPerRow = 14) { (outputs, placeholder) =>
        val query =
          s"""
           |INSERT INTO outputs ("block_hash",
           |                     "tx_hash",
           |                     "block_timestamp",
           |                     "output_type",
           |                     "hint",
           |                     "key",
           |                     "amount",
           |                     "address",
           |                     "tokens",
           |                     "main_chain",
           |                     "lock_time",
           |                     "message",
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
              params >> output.outputType
              params >> output.hint
              params >> output.key
              params >> output.amount
              params >> output.address
              params >> output.tokens
              params >> output.mainChain
              params >> output.lockTime
              params >> output.message
              params >> output.outputOrder
              params >> output.txOrder
          }

        SQLActionBuilder(
          queryParts = query,
          unitPConv  = parameters
        ).asUpdate
      }
  // scalastyle:on magic.number

  private def insertTxPerAddressFromOutputs(outputs: Iterable[OutputEntity]): DBActionW[Int] = {
    QuerySplitter.splitUpdates(rows = outputs, columnsPerRow = 6) { (outputs, placeholder) =>
      val query =
        s"""
           |INSERT INTO transaction_per_addresses (address, tx_hash, block_hash, block_timestamp, tx_order, main_chain)
           |VALUES $placeholder
           |ON CONFLICT (tx_hash, block_hash, address)
           |DO NOTHING
           |""".stripMargin

      val parameters: SetParameter[Unit] =
        (_: Unit, params: PositionedParameters) =>
          outputs foreach { output =>
            params >> output.address
            params >> output.txHash
            params >> output.blockHash
            params >> output.timestamp
            params >> output.txOrder
            params >> output.mainChain
        }

      SQLActionBuilder(
        queryParts = query,
        unitPConv  = parameters
      ).asUpdate
    }
  }

  private def insertTokensFromOutputs(outputs: Iterable[OutputEntity]): DBActionRWT[Unit] = {
    val tokenOutputs = outputs.flatMap { output =>
      output.tokens match {
        case None => Iterable.empty
        case Some(tokens) =>
          tokens.map(token => (token, output))
      }
    }

    DBIOAction
      .seq(
        insertTokenOutputs(tokenOutputs),
        insertTransactionTokenFromOutputs(tokenOutputs),
        insertTokenPerAddressFromOutputs(tokenOutputs),
        insertTokenInfoFromOutputs(tokenOutputs)
      )
      .transactionally
  }

  // scalastyle:off magic.number
  private def insertTokenOutputs(tokenOutputs: Iterable[(Token, OutputEntity)]): DBActionW[Int] = {
    QuerySplitter.splitUpdates(rows = tokenOutputs, columnsPerRow = 14) {
      (tokenOutputs, placeholder) =>
        val query =
          s"""
           |INSERT INTO token_outputs ("block_hash",
           |                     "tx_hash",
           |                     "block_timestamp",
           |                     "output_type",
           |                     "hint",
           |                     "key",
           |                     "token",
           |                     "amount",
           |                     "address",
           |                     "main_chain",
           |                     "lock_time",
           |                     "message",
           |                     "output_order",
           |                     "tx_order")
           |VALUES $placeholder
           |ON CONFLICT
           |    ON CONSTRAINT token_outputs_pk
           |    DO NOTHING
           |""".stripMargin

        val parameters: SetParameter[Unit] =
          (_: Unit, params: PositionedParameters) =>
            tokenOutputs foreach {
              case (token, output) =>
                params >> output.blockHash
                params >> output.txHash
                params >> output.timestamp
                params >> output.outputType
                params >> output.hint
                params >> output.key
                params >> token.id
                params >> token.amount
                params >> output.address
                params >> output.mainChain
                params >> output.lockTime
                params >> output.message
                params >> output.outputOrder
                params >> output.txOrder
          }

        SQLActionBuilder(
          queryParts = query,
          unitPConv  = parameters
        ).asUpdate
    }
  }
  // scalastyle:on magic.number

  private def insertTransactionTokenFromOutputs(
      tokenOutputs: Iterable[(Token, OutputEntity)]): DBActionW[Int] = {
    QuerySplitter.splitUpdates(rows = tokenOutputs, columnsPerRow = 6) {
      (tokenOutputs, placeholder) =>
        val query =
          s"""
           |  INSERT INTO transaction_per_token (tx_hash, block_hash, token, block_timestamp, tx_order, main_chain )
           |  VALUES $placeholder
           |  ON CONFLICT (tx_hash, block_hash, token) DO NOTHING
           |""".stripMargin

        val parameters: SetParameter[Unit] =
          (_: Unit, params: PositionedParameters) =>
            tokenOutputs foreach {
              case (token, output) =>
                params >> output.txHash
                params >> output.blockHash
                params >> token
                params >> output.timestamp
                params >> output.txOrder
                params >> output.mainChain
          }

        SQLActionBuilder(
          queryParts = query,
          unitPConv  = parameters
        ).asUpdate
    }
  }

  private def insertTokenPerAddressFromOutputs(
      tokenOutputs: Iterable[(Token, OutputEntity)]): DBActionW[Int] = {
    QuerySplitter.splitUpdates(rows = tokenOutputs, columnsPerRow = 7) {
      (tokenOutputs, placeholder) =>
        val query =
          s"""
           |INSERT INTO token_tx_per_addresses (address, tx_hash, block_hash, block_timestamp, tx_order, main_chain, token)
           |VALUES $placeholder
           |ON CONFLICT (tx_hash, block_hash, address, token)
           |DO NOTHING
           |""".stripMargin

        val parameters: SetParameter[Unit] =
          (_: Unit, params: PositionedParameters) =>
            tokenOutputs foreach {
              case (token, output) =>
                params >> output.address
                params >> output.txHash
                params >> output.blockHash
                params >> output.timestamp
                params >> output.txOrder
                params >> output.mainChain
                params >> token
          }

        SQLActionBuilder(
          queryParts = query,
          unitPConv  = parameters
        ).asUpdate
    }
  }

  @SuppressWarnings(Array("org.wartremover.warts.IterableOps"))
  private def insertTokenInfoFromOutputs(
      tokenOutputs: Iterable[(Token, OutputEntity)]): DBActionW[Int] = {
    val tokens = tokenOutputs
      .groupBy { case (token, _) => token.id }
      .map {
        case (token, groups) =>
          val timestamp = groups.map { case (_, output) => output.timestamp }.max
          (token, timestamp)
      }
      .toSeq

    QuerySplitter.splitUpdates(rows = tokens, columnsPerRow = 2) { (tokens, placeholder) =>
      val query =
        s"""
           |INSERT INTO token_info (token, last_used)
           |VALUES $placeholder
           |ON CONFLICT
           |ON CONSTRAINT token_info_pkey
           |DO UPDATE
           |SET last_used = EXCLUDED.last_used
           |""".stripMargin

      val parameters: SetParameter[Unit] =
        (_: Unit, params: PositionedParameters) =>
          tokens foreach {
            case (token, timestamp) =>
              params >> token
              params >> timestamp
        }

      SQLActionBuilder(
        queryParts = query,
        unitPConv  = parameters
      ).asUpdate
    }
  }

  // format: off
  def outputsFromTxsSQL(txHashes: Seq[Transaction.Hash]):
  DBActionR[Seq[(Transaction.Hash, Int, OutputEntity.OutputType, Int, Hash, U256, Address,
    Option[Seq[Token]],Option[TimeStamp], Option[ByteString], Option[Transaction.Hash])]] = {
  // format: on
    if (txHashes.nonEmpty) {
      val values = txHashes.map(hash => s"'\\x$hash'").mkString(",")
      sql"""
    SELECT
      outputs.tx_hash,
      outputs.output_order,
      outputs.output_type,
      outputs.hint,
      outputs.key,
      outputs.amount,
      outputs.address,
      outputs.tokens,
      outputs.lock_time,
      outputs.message,
      inputs.tx_hash
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
          (output.outputType,
           output.hint,
           output.key,
           output.amount,
           output.address,
           output.tokens,
           output.lockTime,
           output.message,
           input.map(_.txHash))
      }
  }

  @SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
  val toApiOutput: (
      (OutputEntity.OutputType,
       Int,
       Hash,
       U256,
       Address,
       Option[Seq[Token]],
       Option[TimeStamp],
       Option[ByteString],
       Option[Transaction.Hash])) => Output = {
    (outputType: OutputEntity.OutputType,
     hint: Int,
     key: Hash,
     amount: U256,
     address: Address,
     tokens: Option[Seq[Token]],
     lockTime: Option[TimeStamp],
     message: Option[ByteString],
     spent: Option[Transaction.Hash]) =>
      {

        outputType match {
          case OutputEntity.Asset =>
            AssetOutput(hint, key, amount, address, tokens, lockTime, message, spent)
          case OutputEntity.Contract => ContractOutput(hint, key, amount, address, tokens, spent)
        }
      }
  }.tupled
}
