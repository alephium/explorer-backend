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

  private val countBlockHashTransactionsQuery = Compiled { blockHash: Rep[BlockEntry.Hash] =>
    transactionsTable.filter(_.blockHash === blockHash).length
  }

  def countBlockHashTransactions(blockHash: BlockEntry.Hash): DBActionR[Int] =
    countBlockHashTransactionsQuery(blockHash).result

  def countAddressTransactions(address: Address): DBActionR[Int] =
    getTxNumberByAddressQuery(address).result

  private val getTransactionQuery = Compiled { txHash: Rep[Transaction.Hash] =>
    transactionsTable
      .filter(_.hash === txHash)
      .map(tx => (tx.blockHash, tx.timestamp, tx.startGas, tx.gasPrice))
  }

  def getTransactionAction(txHash: Transaction.Hash): DBActionR[Option[Transaction]] =
    getTransactionQuery(txHash).result.headOption.flatMap {
      case None => DBIOAction.successful(None)
      case Some((blockHash, timestamp, startGas, gasPrice)) =>
        getKnownTransactionAction(txHash, blockHash, timestamp, startGas, gasPrice).map(Some.apply)
    }

  private val mainInputs  = inputsTable.filter(_.mainChain)
  private val mainOutputs = outputsTable.filter(_.mainChain)

  private val getTxHashesByBlockHashQuery = Compiled { (blockHash: Rep[BlockEntry.Hash]) =>
    transactionsTable
      .filter(_.blockHash === blockHash)
      .map(tx => (tx.hash, tx.blockHash, tx.timestamp))
  }

  private val getTxHashesByBlockHashWithPaginationQuery = Compiled {
    (blockHash: Rep[BlockEntry.Hash], toDrop: ConstColumn[Long], limit: ConstColumn[Long]) =>
      transactionsTable
        .filter(_.blockHash === blockHash)
        .map(tx => (tx.hash, tx.blockHash, tx.timestamp))
        .drop(toDrop)
        .take(limit)
  }

  private val getTxNumberByAddressQuery = Compiled { (address: Rep[Address]) =>
    mainInputs
      .join(mainOutputs)
      .on(_.outputRefKey === _.key)
      .filter(_._2.address === address)
      .map { case (input, _) => input.txHash }
      .union(
        mainOutputs
          .filter(_.address === address)
          .map(out => out.txHash)
      )
      .length
  }

  private val getTxHashesByAddressQuery = Compiled {
    (address: Rep[Address], toDrop: ConstColumn[Long], limit: ConstColumn[Long]) =>
      mainInputs
        .join(mainOutputs)
        .on(_.outputRefKey === _.key)
        .filter(_._2.address === address)
        .map { case (input, _) => input.txHash }
        .union(
          mainOutputs
            .filter(_.address === address)
            .map(out => out.txHash)
        )
        .join(transactionsTable)
        .on(_ === _.hash)
        .sortBy { case (_, tx) => (tx.timestamp.desc, tx.order) }
        .map { case (_, tx) => (tx.hash, tx.blockHash, tx.timestamp) }
        .drop(toDrop)
        .take(limit)
  }

  private def inputsFromTxs(txHashes: Seq[Transaction.Hash]) = {
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
           (input.scriptHint,
            input.outputRefKey,
            input.unlockScript,
            output.txHash,
            output.address,
            output.amount))
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

  def getTransactionsByBlockHash(blockHash: BlockEntry.Hash): DBActionR[Seq[Transaction]] = {
    for {
      txHashesTs <- getTxHashesByBlockHashQuery(blockHash).result
      txs        <- getTransactions(txHashesTs)
    } yield txs
  }

  def getTransactionsByBlockHashWithPagination(
      blockHash: BlockEntry.Hash,
      pagination: Pagination): DBActionR[Seq[Transaction]] = {
    val offset = pagination.offset.toLong
    val limit  = pagination.limit.toLong
    val toDrop = offset * limit
    for {
      txHashesTs <- getTxHashesByBlockHashWithPaginationQuery((blockHash, toDrop, limit)).result
      txs        <- getTransactions(txHashesTs)
    } yield txs
  }

  def getTransactionsByAddress(address: Address,
                               pagination: Pagination): DBActionR[Seq[Transaction]] = {
    val offset = pagination.offset.toLong
    val limit  = pagination.limit.toLong
    val toDrop = offset * limit
    for {
      txHashesTs <- getTxHashesByAddressQuery((address, toDrop, limit)).result
      txs        <- getTransactions(txHashesTs)
    } yield txs
  }

  def getTransactions(txHashesTs: Seq[(Transaction.Hash, BlockEntry.Hash, TimeStamp)])
    : DBActionR[Seq[Transaction]] = {
    val txHashes = txHashesTs.map(_._1)
    for {
      ins <- inputsFromTxs(txHashes).result
      ous <- outputsFromTxs(txHashes).result
      gas <- gasFromTxs(txHashes).result
    } yield {
      val insByTx = ins.groupBy(_._1).view.mapValues(_.map { case (_, in)   => toApiInput(in) })
      val ousByTx = ous.groupBy(_._1).view.mapValues(_.map { case (_, o)    => toApiOutput(o) })
      val gasByTx = gas.groupBy(_._1).view.mapValues(_.map { case (_, s, g) => (s, g) })
      txHashesTs.map {
        case (tx, bh, ts) =>
          val ins                  = insByTx.getOrElse(tx, Seq.empty)
          val ous                  = ousByTx.getOrElse(tx, Seq.empty)
          val gas                  = gasByTx.getOrElse(tx, Seq.empty)
          val (startGas, gasPrice) = gas.headOption.getOrElse((0, U256.Zero))
          Transaction(tx, bh, ts, ins, ous, startGas, gasPrice)
      }
    }
  }

  private def gasFromTxs(txHashes: Seq[Transaction.Hash]) = {
    transactionsTable.filter(_.hash inSet txHashes).map(tx => (tx.hash, tx.startGas, tx.gasPrice))
  }

  private val getInputsQuery = Compiled { (txHash: Rep[Transaction.Hash]) =>
    mainInputs
      .filter(_.txHash === txHash)
      .join(mainOutputs)
      .on(_.outputRefKey === _.key)
      .map {
        case (input, output) =>
          (input.scriptHint,
           input.outputRefKey,
           input.unlockScript,
           output.txHash,
           output.address,
           output.amount)
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
                                        timestamp: TimeStamp,
                                        startGas: Int,
                                        gasPrice: U256): DBActionR[Transaction] =
    for {
      ins  <- getInputsQuery(txHash).result
      outs <- getOutputsQuery(txHash).result
    } yield {
      Transaction(txHash,
                  blockHash,
                  timestamp,
                  ins.map(toApiInput),
                  outs.map(toApiOutput),
                  startGas,
                  gasPrice)
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
     txHash: Transaction.Hash,
     address: Address,
     amount: U256) =>
      Input(Output.Ref(scriptHint, key), unlockScript, txHash, address, amount)
  }.tupled

  private val toApiOutput = (Output.apply _).tupled

  // switch logger.trace when we can disable debugging mode
  protected def debugShow(query: slickProfile.ProfileAction[_, _, _]) = {
    print(s"${query.statements.mkString}\n")
  }
}
