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
import org.alephium.explorer.persistence.{DBActionR, DBActionW}
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.persistence.schema.{InputSchema, OutputSchema, TransactionSchema}
import org.alephium.util.TimeStamp

trait TransactionQueries
    extends TransactionSchema
    with InputSchema
    with OutputSchema
    with StrictLogging {

  implicit def executionContext: ExecutionContext
  val config: DatabaseConfig[JdbcProfile]
  import config.profile.api._

  def insertTransactionFromBlockQuery(blockEntity: BlockEntity): DBActionW[Unit] = {
    for {
      _ <- DBIOAction.sequence(blockEntity.transactions.map(transactionsTable.insertOrUpdate))
      _ <- DBIOAction.sequence(blockEntity.inputs.map(inputsTable.insertOrUpdate))
      _ <- DBIOAction.sequence(blockEntity.outputs.map(outputsTable.insertOrUpdate))
    } yield ()
  }

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
      .filter(output => output.mainChain && output.address === address)
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
      .filter(input => input.mainChain && input.txHash === txHash)
      .joinLeft(outputsTable.filter(_.mainChain))
      .on(_.outputRefKey === _.key)
      .map {
        case (input, outputOpt) =>
          (input.scriptHint,
           input.outputRefKey,
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
      .filter(output => output.mainChain && output.txHash === txHash)
      .map(output => (output.key, output.address, output.amount))
      .joinLeft(inputsTable)
      .on(_._1 === _.outputRefKey)
      .map { case (output, input) => (output._3, output._2, input.map(_.txHash)) }
  }

  private val toApiOutput = (Output.apply _).tupled

  private def getKnownTransactionAction(txHash: Transaction.Hash,
                                        timestamp: TimeStamp): DBActionR[Transaction] =
    for {
      ins  <- getInputsQuery(txHash).result
      outs <- getOutputsQuery(txHash).result
    } yield {
      Transaction(txHash, timestamp, ins.map(toApiInput), outs.map(toApiOutput))
    }

  private val getBalanceQuery = Compiled { address: Rep[Address] =>
    outputsTable
      .filter(output => output.mainChain && output.address === address)
      .map(output => (output.key, output.amount))
      .joinLeft(inputsTable.filter(_.mainChain))
      .on(_._1 === _.outputRefKey)
      .filter(_._2.isEmpty)
      .map(_._1._2)
      .sum
  }

  def getBalanceAction(address: Address): DBActionR[Double] =
    getBalanceQuery(address).result.map(_.getOrElse(0.0))

  // switch logger.trace when we can disable debugging mode
  protected def debugShow(query: slickProfile.ProfileAction[_, _, _]) = {
    print(s"${query.statements.mkString}\n")
  }
}
