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
import org.alephium.util.{TimeStamp, U256}

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

  def insertAllTransactionFromBlockQuerys(blockEntities: Seq[BlockEntity]): DBActionW[Unit] = {
    DBIOAction.sequence(blockEntities.map(insertTransactionFromBlockQuery)).map(_ => ())
  }

  private val listTransactionsQuery = Compiled { blockHash: Rep[BlockEntry.Hash] =>
    transactionsTable
      .filter(_.blockHash === blockHash)
      .map(tx => (tx.hash, tx.timestamp))
  }

  def listTransactionsAction(blockHash: BlockEntry.Hash): DBActionR[Seq[Transaction]] =
    for {
      txEntities <- listTransactionsQuery(blockHash).result
      txs <- DBIOAction.sequence(
        txEntities.map(pair => getKnownTransactionAction(pair._1, blockHash, pair._2)))
    } yield txs

  private val countTransactionsQuery = Compiled { blockHash: Rep[BlockEntry.Hash] =>
    transactionsTable.filter(_.blockHash === blockHash).length
  }

  def countTransactionsAction(blockHash: BlockEntry.Hash): DBActionR[Int] =
    countTransactionsQuery(blockHash).result

  private val getTransactionQuery = Compiled { txHash: Rep[Transaction.Hash] =>
    transactionsTable.filter(_.hash === txHash).map(tx => (tx.blockHash, tx.timestamp))
  }

  def getTransactionAction(txHash: Transaction.Hash): DBActionR[Option[Transaction]] =
    getTransactionQuery(txHash).result.headOption.flatMap {
      case None => DBIOAction.successful(None)
      case Some((blockHash, timestamp)) =>
        getKnownTransactionAction(txHash, blockHash, timestamp).map(Some.apply)
    }

  private val mainInputs  = inputsTable.filter(_.mainChain)
  private val mainOutputs = outputsTable.filter(_.mainChain)

  private val getTxHashesQuery = Compiled {
    (address: Rep[Address], toDrop: ConstColumn[Long], limit: ConstColumn[Long]) =>
      mainInputs
        .join(mainOutputs)
        .on(_.outputRefKey === _.key)
        .filter(_._2.address === address)
        .map { case (input, _) => (input.txHash, input.blockHash, input.timestamp) }
        .union(
          mainOutputs
            .filter(_.address === address)
            .map(out => (out.txHash, out.blockHash, out.timestamp))
        )
        .sortBy { case (txHash, _, timestamp) => (timestamp.desc, txHash) }
        .drop(toDrop)
        .take(limit)
  }

  private def inputsFromTxs(txHashes: Seq[Transaction.Hash]) = {
    mainInputs
      .filter(_.txHash inSet txHashes)
      .joinLeft(mainOutputs)
      .on {
        case (input, outputs) =>
          input.outputRefKey === outputs.key
      }
      .map {
        case (input, outputOpt) =>
          (input.txHash,
           (input.scriptHint,
            input.outputRefKey,
            input.unlockScript,
            outputOpt.map(_.txHash),
            outputOpt.map(_.address),
            outputOpt.map(_.amount)))
      }
  }

  private def outputsFromTxs(txHashes: Seq[Transaction.Hash]) = {
    mainOutputs
      .filter(_.txHash inSet txHashes)
      .joinLeft(mainInputs)
      .on {
        case (out, inputs) =>
          out.key === inputs.outputRefKey
      }
      .map {
        case (output, input) =>
          (output.txHash, (output.amount, output.address, output.lockTime, input.map(_.txHash)))
      }
  }

  def getTransactionsByAddress(address: Address,
                               pagination: Pagination): DBActionR[Seq[Transaction]] = {
    val offset          = pagination.offset.toLong
    val limit           = pagination.limit.toLong
    val toDrop          = offset * limit
    val txHashesTsQuery = getTxHashesQuery((address, toDrop, limit))
    for {
      txHashesTs <- txHashesTsQuery.result
      txHashes = txHashesTs.map(_._1)
      ins <- inputsFromTxs(txHashes).result
      ous <- outputsFromTxs(txHashes).result
    } yield {
      val insByTx = ins.groupBy(_._1).view.mapValues(_.map { case (_, in) => toApiInput(in) })
      val ousByTx = ous.groupBy(_._1).view.mapValues(_.map { case (_, o)  => toApiOutput(o) })
      txHashesTs.map {
        case (tx, bh, ts) =>
          val ins = insByTx.getOrElse(tx, Seq.empty)
          val ous = ousByTx.getOrElse(tx, Seq.empty)
          Transaction(tx, bh, ts, ins, ous)
      }
    }
  }

  private val getInputsQuery = Compiled { (txHash: Rep[Transaction.Hash]) =>
    mainInputs
      .filter(_.txHash === txHash)
      .joinLeft(mainOutputs)
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

  private val getOutputsQuery = Compiled { (txHash: Rep[Transaction.Hash]) =>
    outputsTable
      .filter(output => output.mainChain && output.txHash === txHash)
      .map(output => (output.key, output.address, output.amount, output.lockTime))
      .joinLeft(inputsTable)
      .on(_._1 === _.outputRefKey)
      .map { case (output, input) => (output._3, output._2, output._4, input.map(_.txHash)) }
  }

  private def getKnownTransactionAction(txHash: Transaction.Hash,
                                        blockHash: BlockEntry.Hash,
                                        timestamp: TimeStamp): DBActionR[Transaction] =
    for {
      ins  <- getInputsQuery(txHash).result
      outs <- getOutputsQuery(txHash).result
    } yield {
      Transaction(txHash, blockHash, timestamp, ins.map(toApiInput), outs.map(toApiOutput))
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

  def getBalanceAction(address: Address): DBActionR[U256] =
    getBalanceQuery(address).result.map(_.getOrElse(U256.Zero))

  private val toApiInput = {
    (scriptHint: Int,
     key: Hash,
     unlockScript: Option[String],
     txHashOpt: Option[Transaction.Hash],
     addressOpt: Option[Address],
     amountOpt: Option[U256]) =>
      Input(Output.Ref(scriptHint, key), unlockScript, txHashOpt, addressOpt, amountOpt)
  }.tupled

  private val toApiOutput = (Output.apply _).tupled

  // switch logger.trace when we can disable debugging mode
  protected def debugShow(query: slickProfile.ProfileAction[_, _, _]) = {
    print(s"${query.statements.mkString}\n")
  }
}
