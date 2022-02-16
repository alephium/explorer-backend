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

trait TransactionQueries
    extends TransactionSchema
    with InputSchema
    with OutputSchema
    with StrictLogging {

  implicit def executionContext: ExecutionContext
  val config: DatabaseConfig[JdbcProfile]
  import config.profile.api._

  private val mainTransactions = transactionsTable.filter(_.mainChain)
  private val mainInputs       = inputsTable.filter(_.mainChain)
  private val mainOutputs      = outputsTable.filter(_.mainChain)

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
    mainTransactions
      .filter(_.hash === txHash)
      .map(tx => (tx.blockHash, tx.timestamp, tx.gasAmount, tx.gasPrice))
  }

  def getTransactionAction(txHash: Transaction.Hash): DBActionR[Option[Transaction]] =
    getTransactionQuery(txHash).result.headOption.flatMap {
      case None => DBIOAction.successful(None)
      case Some((blockHash, timestamp, gasAmount, gasPrice)) =>
        getKnownTransactionAction(txHash, blockHash, timestamp, gasAmount, gasPrice).map(Some.apply)
    }

  private val getTxHashesByBlockHashQuery = Compiled { (blockHash: Rep[BlockEntry.Hash]) =>
    transactionsTable
      .filter(_.blockHash === blockHash)
      .sortBy(_.txIndex)
      .map(tx => (tx.hash, tx.blockHash, tx.timestamp, tx.txIndex))
  }

  private val getTxHashesByBlockHashWithPaginationQuery = Compiled {
    (blockHash: Rep[BlockEntry.Hash], toDrop: ConstColumn[Long], limit: ConstColumn[Long]) =>
      transactionsTable
        .filter(_.blockHash === blockHash)
        .sortBy(_.txIndex)
        .map(tx => (tx.hash, tx.blockHash, tx.timestamp, tx.txIndex))
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

  def countAddressTransactionsSQL(address: Address): DBActionSR[Int] = {
    val query = s"""
    SELECT COUNT(*)
    FROM (
      (SELECT inputs.tx_hash
        FROM inputs
        JOIN outputs ON inputs.output_ref_key = outputs.key AND outputs.main_chain = true
        WHERE inputs.main_chain = true)
    UNION
    SELECT tx_hash from outputs WHERE main_chain = true AND address = '$address'
    ) tx_hashes
    """
    sql"""#$query""".as[Int]
  }

  val getTxHashesByAddressQuery = Compiled {
    (address: Rep[Address], toDrop: ConstColumn[Long], limit: ConstColumn[Long]) =>
      mainInputs
        .join(mainOutputs)
        .on(_.outputRefKey === _.key)
        .filter(_._2.address === address)
        .map { case (input, _) => (input.txHash, input.blockHash, input.timestamp, input.txIndex) }
        .union(
          mainOutputs
            .filter(_.address === address)
            .map(out => (out.txHash, out.blockHash, out.timestamp, out.txIndex))
        )
        .sortBy { case (_, _, timestamp, txIndex) => (timestamp.desc, txIndex) }
        .drop(toDrop)
        .take(limit)
  }

  def getTxHashesByAddressQuerySQL(
      address: Address,
      offset: Int,
      limit: Int): DBActionSR[(Transaction.Hash, BlockEntry.Hash, TimeStamp, Int)] = {
    sql"""
    (
      SELECT inputs.tx_hash, inputs.block_hash, inputs.timestamp, inputs.tx_index
      FROM inputs
      JOIN outputs ON outputs.main_chain = true AND inputs.output_ref_key = outputs.key AND outputs.address = '#$address'
      WHERE inputs.main_chain = true
      UNION
      SELECT tx_hash, block_hash, timestamp, tx_index from outputs
      WHERE main_chain = true AND address = '#$address'
    )
    ORDER BY timestamp DESC, tx_index
    LIMIT #$limit
    OFFSET #$offset
    """.as
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

  def getTransactionsByAddressSQL(address: Address,
                                  pagination: Pagination): DBActionR[Seq[Transaction]] = {
    val offset = pagination.offset
    val limit  = pagination.limit
    val toDrop = offset * limit
    for {
      txHashesTs <- getTxHashesByAddressQuerySQL(address, toDrop, limit)
      txs        <- getTransactionsSQL(txHashesTs)
    } yield txs
  }

  def getTransactions(txHashesTs: Seq[(Transaction.Hash, BlockEntry.Hash, TimeStamp, Int)])
    : DBActionR[Seq[Transaction]] = {
    val txHashes = txHashesTs.map(_._1)
    for {
      inputs  <- inputsFromTxs(txHashes).result
      outputs <- outputsFromTxs(txHashes).result
      gases   <- gasFromTxs(txHashes).result
    } yield {
      buildTransaction(txHashesTs, inputs, outputs, gases)
    }
  }

  def getTransactionsSQL(txHashesTs: Seq[(Transaction.Hash, BlockEntry.Hash, TimeStamp, Int)])
    : DBActionR[Seq[Transaction]] = {
    val txHashes = txHashesTs.map(_._1)
    for {
      inputs  <- inputsFromTxsSQL(txHashes)
      outputs <- outputsFromTxsSQL(txHashes)
      gases   <- gasFromTxsSQL(txHashes)
    } yield {
      buildTransaction(txHashesTs, inputs, outputs, gases)
    }
  }

  // format: off
  private def buildTransaction(
      txHashesTs: Seq[(Transaction.Hash, BlockEntry.Hash, TimeStamp, Int)],
      inputs: Seq[(Transaction.Hash, Int, Int, Hash, Option[String], Transaction.Hash, Address, U256)],
      outputs: Seq[(Transaction.Hash, Int, Int, Hash, U256, Address, Option[TimeStamp], Option[Transaction.Hash])],
      gases: Seq[(Transaction.Hash, Int, U256)]) = {
  // format: on
    val insByTx = inputs.groupBy(_._1).view.mapValues { values =>
      values
        .sortBy(_._2)
        .map {
          case (_, _, hint, key, unlockScript, txHashRef, address, amount) =>
            toApiInput((hint, key, unlockScript, txHashRef, address, amount))
        }
    }
    val ousByTx = outputs.groupBy(_._1).view.mapValues { values =>
      values
        .sortBy(_._2)
        .map {
          case (_, _, hint, key, amount, address, lockTime, spent) =>
            toApiOutput((hint, key, amount, address, lockTime, spent))
        }
    }
    val gasByTx = gases.groupBy(_._1).view.mapValues(_.map { case (_, s, g) => (s, g) })
    txHashesTs.map {
      case (tx, bh, ts, _) =>
        val ins                   = insByTx.getOrElse(tx, Seq.empty)
        val ous                   = ousByTx.getOrElse(tx, Seq.empty)
        val gas                   = gasByTx.getOrElse(tx, Seq.empty)
        val (gasAmount, gasPrice) = gas.headOption.getOrElse((0, U256.Zero))
        Transaction(tx, bh, ts, ins, ous, gasAmount, gasPrice)
    }
  }

  @SuppressWarnings(Array("org.wartremover.warts.PublicInference"))
  def gasFromTxs(txHashes: Seq[Transaction.Hash]) = {
    mainTransactions.filter(_.hash inSet txHashes).map(tx => (tx.hash, tx.gasAmount, tx.gasPrice))
  }

  def gasFromTxsSQL(txHashes: Seq[Transaction.Hash]): DBActionSR[(Transaction.Hash, Int, U256)] = {
    val values = txHashes.map(hash => s"'\\x$hash'").mkString(",")
    sql"""
    SELECT hash, gas_amount, gas_price
    FROM transactions
    WHERE main_chain = true AND hash IN (#$values)
    """.as
  }

  private val getInputsQuery = Compiled { (txHash: Rep[Transaction.Hash]) =>
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

  private val getOutputsQuery = Compiled { (txHash: Rep[Transaction.Hash]) =>
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

  private def getKnownTransactionAction(txHash: Transaction.Hash,
                                        blockHash: BlockEntry.Hash,
                                        timestamp: TimeStamp,
                                        gasAmount: Int,
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
                  gasAmount,
                  gasPrice)
    }

  def getBalanceQuerySQL(address: Address): DBActionSR[(U256, Option[TimeStamp])] = {
    val query = s"""
        SELECT outputs.amount, outputs.lock_time
        FROM outputs
        LEFT JOIN inputs ON outputs.key = inputs.output_ref_key
        WHERE outputs.main_chain = true AND outputs.address = '$address' AND inputs.block_hash IS NULL
      """
    sql"#$query".as[(U256, Option[TimeStamp])]
  }

  val getBalanceQuery = Compiled { address: Rep[Address] =>
    mainOutputs
      .filter(output => output.address === address)
      .joinLeft(mainInputs)
      .on(_.key === _.outputRefKey)
      .filter(_._2.isEmpty)
      .map { case (output, _) => (output.amount, output.lockTime) }
  }

  def getBalanceAction(address: Address): DBActionR[(U256, U256)] = {
    getBalanceQuery(address).result.map { outputs =>
      sumBalance(outputs)
    }
  }

  private def sumBalance(outputs: Seq[(U256, Option[TimeStamp])]): (U256, U256) = {
    val now = TimeStamp.now()
    outputs.foldLeft((U256.Zero, U256.Zero)) {
      case ((total, locked), (amount, lockTime)) =>
        val newTotal = total.addUnsafe(amount)
        val newLocked = if (lockTime.map(_.isBefore(now)).getOrElse(true)) {
          locked
        } else {
          locked.addUnsafe(amount)
        }
        (newTotal, newLocked)
    }
  }

  def getBalanceActionSQL(address: Address): DBActionR[(U256, U256)] = {
    getBalanceQuerySQL(address).map(sumBalance)
  }

  private val toApiInput = {
    (hint: Int,
     key: Hash,
     unlockScript: Option[String],
     txHash: Transaction.Hash,
     address: Address,
     amount: U256) =>
      Input(Output.Ref(hint, key), unlockScript, txHash, address, amount)
  }.tupled

  private val toApiOutput = (Output.apply _).tupled

  // switch logger.trace when we can disable debugging mode
  protected def debugShow(query: slickProfile.ProfileAction[_, _, _]) = {
    print(s"${query.statements.mkString}\n")
  }
}
