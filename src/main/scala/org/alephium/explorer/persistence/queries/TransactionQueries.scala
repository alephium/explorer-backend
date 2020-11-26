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
import org.alephium.explorer.api.model.{Address, BlockEntry, Input, Output, Transaction}
import org.alephium.explorer.persistence.{DBActionR, DBActionRW, DBActionW}
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.persistence.schema.{InputSchema, OutputSchema, TransactionSchema}
import org.alephium.util.{AVector, TimeStamp}

trait TransactionQueries
    extends TransactionSchema
    with InputSchema
    with OutputSchema
    with StrictLogging {

  implicit def executionContext: ExecutionContext
  val config: DatabaseConfig[JdbcProfile]
  import config.profile.api._

  def insertTransactionFromBlockQuery(blockEntity: BlockEntity): DBActionW[Unit] =
    ((transactionsTable ++= blockEntity.transactions.toArray) >>
      (inputsTable ++= blockEntity.inputs.toArray) >>
      (outputsTable ++= blockEntity.outputs.toArray))
      .map(_ => ())

  private val listTransactionsQuery = Compiled { blockHash: Rep[BlockEntry.Hash] =>
    transactionsTable
      .filter(_.blockHash === blockHash)
      .sortBy(_.timestamp.asc)
      .map(tx => (tx.hash, tx.timestamp))
  }

  def listTransactionsAction(blockHash: BlockEntry.Hash): DBActionR[Seq[Transaction]] =
    for {
      txEntities <- listTransactionsQuery(blockHash).result
      txs <- DBIOAction.sequence(
        txEntities.map(pair => getKnownTransactionAction(pair._1, pair._2)))
    } yield txs

  private val getTimestampQuery = Compiled { txHash: Rep[Transaction.Hash] =>
    transactionsTable.filter(_.hash === txHash).map(_.timestamp)
  }

  def getTransactionAction(txHash: Transaction.Hash): DBActionR[Option[Transaction]] =
    getTimestampQuery(txHash).result.headOption.flatMap {
      case None            => DBIOAction.successful(None)
      case Some(timestamp) => getKnownTransactionAction(txHash, timestamp).map(Some.apply)
    }

  private val getTxHashesQuery = Compiled { (address: Rep[Address], txLimit: ConstColumn[Long]) =>
    outputsTable
      .filter(_.address === address)
      .map(out => (out.txHash, out.timestamp))
      .distinct
      .sortBy { case (_, timestamp) => timestamp.asc }
      .take(txLimit)
  }

  def getTransactionsByAddress(address: Address, txLimit: Int): DBActionR[Seq[Transaction]] = {
    for {
      txHashes <- getTxHashesQuery(address -> txLimit.toLong).result
      txs <- DBIOAction.sequence(txHashes.map {
        case (txHash, timestamp) => getKnownTransactionAction(txHash, timestamp)
      })
    } yield txs
  }

  private val getInputsQuery = Compiled { (txHash: Rep[Transaction.Hash]) =>
    inputsTable
      .joinLeft(outputsTable)
      .on(_.key === _.outputRefKey)
      .filter(_._1.txHash === txHash)
      .map {
        case (input, outputOpt) =>
          (input.scriptHint,
           input.key,
           input.unlockScript,
           outputOpt.map(_.txHash),
           outputOpt.map(_.address),
           outputOpt.map(_.amount))
      }
  }

  private val toApiInput = {
    (scriptHint: Int,
     key: Hash,
     unlockScript: String,
     txHashOpt: Option[Transaction.Hash],
     addressOpt: Option[Address],
     amountOpt: Option[Double]) =>
      Input(Output.Ref(scriptHint, key), unlockScript, txHashOpt, addressOpt, amountOpt)
  }.tupled

  private val getOutputsQuery = Compiled { (txHash: Rep[Transaction.Hash]) =>
    outputsTable
      .filter(_.txHash === txHash)
      .map(output => (output.amount, output.address, output.spent))
  }

  private val toApiOutput = (Output.apply _).tupled

  private def getKnownTransactionAction(txHash: Transaction.Hash,
                                        timestamp: TimeStamp): DBActionR[Transaction] =
    for {
      ins  <- getInputsQuery(txHash).result
      outs <- getOutputsQuery(txHash).result
    } yield {
      Transaction(txHash,
                  timestamp,
                  AVector.from(ins.map(toApiInput)),
                  AVector.from(outs.map(toApiOutput)))
    }

  def updateSpentAction(): DBActionRW[Int] = {
    outputsTable
      .filter(_.spent.isEmpty)
      .join(inputsTable)
      .on(_.outputRefKey === _.key)
      .map { case (out, in) => (out, in.txHash) }
      .result
      .map { res =>
        DBIOAction.sequence(res.map {
          case (out, txHash) =>
            outputsTable
              .filter(_.outputRefKey === out.outputRefKey)
              .map(_.spent)
              .update(Some(txHash))
        })
      }
  }.flatMap(_.map(_.sum))

  private val getBalanceQuery = Compiled { address: Rep[Address] =>
    outputsTable
      .joinLeft(inputsTable)
      .on(_.outputRefKey === _.key)
      .filter(_._1.address === address)
      .filter(_._2.isEmpty)
      .map(_._1.amount)
      .sum
  }

  def getBalanceAction(address: Address): DBActionR[Double] =
    getBalanceQuery(address).result.map(_.getOrElse(0.0))

  def debugShow(query: slickProfile.ProfileAction[_, _, _]) = {
    println(s"${query.statements.mkString}")
  }
}
