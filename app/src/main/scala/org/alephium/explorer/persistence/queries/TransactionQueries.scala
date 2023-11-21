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

import scala.collection.immutable.ArraySeq
import scala.concurrent.ExecutionContext

import com.typesafe.scalalogging.StrictLogging
import slick.dbio.DBIOAction
import slick.jdbc.{PositionedParameters, SetParameter, SQLActionBuilder}
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence._
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.persistence.queries.InputQueries._
import org.alephium.explorer.persistence.queries.OutputQueries._
import org.alephium.explorer.persistence.queries.result._
import org.alephium.explorer.persistence.schema._
import org.alephium.explorer.persistence.schema.CustomGetResult._
import org.alephium.explorer.persistence.schema.CustomJdbcTypes._
import org.alephium.explorer.persistence.schema.CustomSetParameter._
import org.alephium.explorer.util.SlickUtil._
import org.alephium.protocol.model.{Address, BlockHash, TransactionId}
import org.alephium.util.{TimeStamp, U256}

object TransactionQueries extends StrictLogging {

  @SuppressWarnings(Array("org.wartremover.warts.PublicInference"))
  val mainTransactions = TransactionSchema.table.filter(_.mainChain)

  def insertAll(
      transactions: ArraySeq[TransactionEntity],
      outputs: ArraySeq[OutputEntity],
      inputs: ArraySeq[InputEntity]
  ): DBActionRWT[Unit] = {
    DBIOAction
      .seq(insertTransactions(transactions), insertInputs(inputs), insertOutputs(outputs))
      .transactionally
  }

  /** Inserts transactions or ignore rows with primary key conflict */
  // scalastyle:off magic.number
  def insertTransactions(transactions: Iterable[TransactionEntity]): DBActionW[Int] =
    QuerySplitter.splitUpdates(rows = transactions, columnsPerRow = 13) {
      (transactions, placeholder) =>
        val query =
          s"""
             |insert into transactions (hash,
             |                          block_hash,
             |                          block_timestamp,
             |                          chain_from,
             |                          chain_to,
             |                          gas_amount,
             |                          gas_price,
             |                          tx_order,
             |                          main_chain,
             |                          script_execution_ok,
             |                          input_signatures,
             |                          script_signatures,
             |                          coinbase)
             |values $placeholder
             |ON CONFLICT ON CONSTRAINT txs_pk
             |    DO NOTHING
             |""".stripMargin

        val parameters: SetParameter[Unit] =
          (_: Unit, params: PositionedParameters) =>
            transactions foreach { transaction =>
              params >> transaction.hash
              params >> transaction.blockHash
              params >> transaction.timestamp
              params >> transaction.chainFrom
              params >> transaction.chainTo
              params >> transaction.gasAmount
              params >> transaction.gasPrice
              params >> transaction.order
              params >> transaction.mainChain
              params >> transaction.scriptExecutionOk
              params >> transaction.inputSignatures
              params >> transaction.scriptSignatures
              params >> transaction.coinbase
            }

        SQLActionBuilder(
          queryParts = query,
          unitPConv = parameters
        ).asUpdate
    }

  private val countBlockHashTransactionsQuery = Compiled { blockHash: Rep[BlockHash] =>
    TransactionSchema.table.filter(_.blockHash === blockHash).length
  }

  def countBlockHashTransactions(blockHash: BlockHash): DBActionR[Int] =
    countBlockHashTransactionsQuery(blockHash).result

  private val getTransactionQuery = Compiled { txHash: Rep[TransactionId] =>
    mainTransactions
      .filter(_.hash === txHash)
      .map(tx =>
        (tx.blockHash, tx.timestamp, tx.gasAmount, tx.gasPrice, tx.scriptExecutionOk, tx.coinbase)
      )
  }

  def getTransactionAction(
      txHash: TransactionId
  )(implicit ec: ExecutionContext): DBActionR[Option[Transaction]] =
    getTransactionQuery(txHash).result.headOption.flatMap {
      case None => DBIOAction.successful(None)
      case Some((blockHash, timestamp, gasAmount, gasPrice, scriptExecutionOk, coinbase)) =>
        getKnownTransactionAction(
          txHash,
          blockHash,
          timestamp,
          gasAmount,
          gasPrice,
          scriptExecutionOk,
          coinbase
        ).map(Some.apply)
    }

  private def getTxHashesByBlockHashQuery(
      blockHash: BlockHash
  ): DBActionSR[(TransactionId, BlockHash, TimeStamp, Int, Boolean)] =
    sql"""
      SELECT hash, block_hash, block_timestamp, tx_order, coinbase
      FROM transactions
      WHERE block_hash = $blockHash
      ORDER BY tx_order
    """.asAS[(TransactionId, BlockHash, TimeStamp, Int, Boolean)]

  private def getTxHashesByBlockHashWithPaginationQuery(
      blockHash: BlockHash,
      pagination: Pagination
  ) =
    sql"""
      SELECT hash, block_hash, block_timestamp, tx_order, coinbase
      FROM transactions
      WHERE block_hash = $blockHash
      ORDER BY tx_order
    """
      .paginate(pagination)
      .asAS[(TransactionId, BlockHash, TimeStamp, Int, Boolean)]

  def countAddressTransactions(address: Address): DBActionSR[Int] = {
    sql"""
    SELECT COUNT(*)
    FROM transaction_per_addresses
    WHERE main_chain = true AND address = $address
    """.asAS[Int]
  }

  def getTxHashesByAddressQuery(
      address: Address,
      pagination: Pagination
  ): DBActionSR[TxByAddressQR] = {
    sql"""
      SELECT #${TxByAddressQR.selectFields}
      FROM transaction_per_addresses
      WHERE main_chain = true AND address = $address
      ORDER BY block_timestamp DESC, tx_order
    """
      .paginate(pagination)
      .asAS[TxByAddressQR]
  }

  /** Get transactions for a list of addresses
    *
    * @param addresses
    *   Addresses to query
    * @param offset
    *   Page number (starting from 0)
    * @param limit
    *   Maximum rows
    * @return
    *   Paginated transactions
    */
  def getTxHashesByAddressesQuery(
      addresses: ArraySeq[Address],
      pagination: Pagination
  ): DBActionSR[TxByAddressQR] =
    if (addresses.isEmpty) {
      DBIOAction.successful(ArraySeq.empty)
    } else {
      val placeholder = paramPlaceholder(1, addresses.size)

      val query =
        s"""
           |SELECT ${TxByAddressQR.selectFields}
           |FROM transaction_per_addresses
           |WHERE main_chain = true
           |  AND address IN $placeholder
           |ORDER BY block_timestamp DESC, tx_order
           |""".stripMargin

      val parameters: SetParameter[Unit] =
        (_: Unit, params: PositionedParameters) => {
          addresses foreach (params >> _)
        }

      SQLActionBuilder(
        queryParts = query,
        unitPConv = parameters
      ).paginate(pagination)
        .asAS[TxByAddressQR]
    }

  /** Get transactions by address for a given time-range
    *
    * @param address
    *   Address to query
    * @param fromTime
    *   From TimeStamp of the time-range
    * @param toTime
    *   To TimeStamp of the time-range
    * @param offset
    *   Page number (starting from 0)
    * @param limit
    *   Maximum rows
    */
  def getTxHashesByAddressQueryTimeRanged(
      address: Address,
      fromTime: TimeStamp,
      toTime: TimeStamp,
      pagination: Pagination
  ): DBActionSR[TxByAddressQR] = {
    sql"""
      SELECT #${TxByAddressQR.selectFields}
      FROM transaction_per_addresses
      WHERE main_chain = true
        AND address = $address
        AND block_timestamp BETWEEN $fromTime AND $toTime
      ORDER BY block_timestamp DESC, tx_order
    """
      .paginate(pagination)
      .asAS[TxByAddressQR]
  }

  def getTransactionsByBlockHash(
      blockHash: BlockHash
  )(implicit ec: ExecutionContext): DBActionSR[Transaction] = {
    for {
      txHashesTs <- getTxHashesByBlockHashQuery(blockHash)
      txs        <- getTransactions(TxByAddressQR(txHashesTs))
    } yield txs
  }

  def getTransactionsByBlockHashWithPagination(blockHash: BlockHash, pagination: Pagination)(
      implicit ec: ExecutionContext
  ): DBActionR[ArraySeq[Transaction]] = {
    for {
      txHashesTs <- getTxHashesByBlockHashWithPaginationQuery(blockHash, pagination)
      txs        <- getTransactions(TxByAddressQR(txHashesTs))
    } yield txs
  }

  def getTransactionsByAddress(address: Address, pagination: Pagination)(implicit
      ec: ExecutionContext
  ): DBActionR[ArraySeq[Transaction]] = {
    for {
      txHashesTs <- getTxHashesByAddressQuery(address, pagination)
      txs        <- getTransactions(txHashesTs)
    } yield txs
  }

  def getTransactionsByAddresses(addresses: ArraySeq[Address], pagination: Pagination)(implicit
      ec: ExecutionContext
  ): DBActionR[ArraySeq[Transaction]] = {
    for {
      txHashesTs <- getTxHashesByAddressesQuery(addresses, pagination)
      txs        <- getTransactions(txHashesTs)
    } yield txs
  }

  def getTransactionsByAddressTimeRanged(
      address: Address,
      fromTime: TimeStamp,
      toTime: TimeStamp,
      pagination: Pagination
  )(implicit ec: ExecutionContext): DBActionR[ArraySeq[Transaction]] = {
    for {
      txHashesTs <- getTxHashesByAddressQueryTimeRanged(address, fromTime, toTime, pagination)
      txs        <- getTransactions(txHashesTs)
    } yield txs
  }

  def hasAddressMoreTxsThanQuery(address: Address, from: TimeStamp, to: TimeStamp, threshold: Int)(
      implicit ec: ExecutionContext
  ): DBActionR[Boolean] = {
    sql"""
      select 1
      FROM transaction_per_addresses
      WHERE address = $address
      AND main_chain = true
      AND block_timestamp >= $from
      AND block_timestamp < $to
      ORDER BY 1 OFFSET ($threshold) ROWS FETCH NEXT (1) ROWS ONLY;
    """.asAS[Int].headOrNone.map(_.isDefined)
  }

  def streamTxByAddressQR(
      address: Address,
      from: TimeStamp,
      to: TimeStamp
  ): StreamAction[TxByAddressQR] = {
    sql"""
      SELECT #${TxByAddressQR.selectFields}
      FROM transaction_per_addresses
      WHERE address = $address
      AND main_chain = true
      AND block_timestamp >= $from
      AND block_timestamp < $to
      ORDER BY block_timestamp DESC
    """.asAS[TxByAddressQR]
  }

  def getTransactions(
      txHashesTs: ArraySeq[TxByAddressQR]
  )(implicit ec: ExecutionContext): DBActionR[ArraySeq[Transaction]] = {
    if (txHashesTs.nonEmpty) {
      val hashes = txHashesTs.map(_.hashes())
      for {
        inputs  <- inputsFromTxs(hashes)
        outputs <- outputsFromTxs(hashes)
        gases   <- infoFromTxs(hashes)
      } yield {
        buildTransaction(txHashesTs, inputs, outputs, gases)
      }
    } else {
      DBIOAction.successful(ArraySeq.empty)
    }
  }

  private val timestampTZ = "to_timestamp(block_timestamp/1000.0) AT TIME ZONE 'UTC'"

  private val hourlyQuery =
    s"DATE_TRUNC('HOUR', $timestampTZ) + ((CEILING((EXTRACT(MINUTE FROM $timestampTZ) + EXTRACT(SECOND FROM $timestampTZ)/60) / 60)) * INTERVAL '1 HOUR')"

  private val dailyQuery =
    s"""DATE_TRUNC('DAY', $timestampTZ) +
      ((CEILING((EXTRACT(HOUR FROM $timestampTZ)*60 + EXTRACT(MINUTE FROM $timestampTZ) + EXTRACT(SECOND FROM $timestampTZ)/60)/60/24)) * INTERVAL '1 DAY')
    """

  def sumAddressOutputs2(address: Address, from: TimeStamp, to: TimeStamp, intervalType: IntervalType)(implicit
      ec: ExecutionContext
  ): DBActionSR[(Option[U256], TimeStamp)] = {

    val dateGroup = intervalType match {
      case IntervalType.Hourly => hourlyQuery
      case IntervalType.Daily  => dailyQuery
    }

    sql"""
      SELECT
        SUM(amount),
        EXTRACT(EPOCH FROM ((#$dateGroup) AT TIME ZONE 'UTC')) * 1000 as ts
      FROM outputs
      WHERE address = $address
      AND main_chain = true
      AND block_timestamp >= $from
      AND block_timestamp <= $to
      GROUP BY ts
      """.asAS[(Option[U256], TimeStamp)]
  }

  def sumAddressOutputs(address: Address, from: TimeStamp, to: TimeStamp)(implicit
      ec: ExecutionContext
  ): DBActionR[U256] = {
    sql"""
      SELECT SUM(amount)
      FROM outputs
      WHERE address = $address
      AND main_chain = true
      AND block_timestamp >= $from
      AND block_timestamp <= $to
    """.asAS[Option[U256]].exactlyOne.map(_.getOrElse(U256.Zero))
  }

  def sumAddressInputs(address: Address, from: TimeStamp, to: TimeStamp)(implicit
      ec: ExecutionContext
  ): DBActionR[U256] = {
    sql"""
      SELECT SUM(output_ref_amount)
      FROM inputs
      WHERE output_ref_address = $address
      AND main_chain = true
      AND block_timestamp >= $from
      AND block_timestamp <= $to
    """.asAS[Option[U256]].exactlyOne.map(_.getOrElse(U256.Zero))
  }

  def sumAddressInputs2(address: Address, from: TimeStamp, to: TimeStamp, intervalType:IntervalType)(implicit
      ec: ExecutionContext
  ): DBActionSR[(Option[U256], TimeStamp)] = {
    val dateGroup = intervalType match {
      case IntervalType.Hourly => hourlyQuery
      case IntervalType.Daily  => dailyQuery
    }

    sql"""
      SELECT
      SUM(output_ref_amount),
        EXTRACT(EPOCH FROM ((#$dateGroup) AT TIME ZONE 'UTC')) * 1000 as ts
      FROM inputs
      WHERE output_ref_address = $address
      AND main_chain = true
      AND block_timestamp >= $from
      AND block_timestamp <= $to
      GROUP BY ts
      """.asAS[(Option[U256], TimeStamp)]
  }

  private def buildTransaction(
      txHashesTs: ArraySeq[TxByAddressQR],
      inputs: ArraySeq[InputsFromTxQR],
      outputs: ArraySeq[OutputsFromTxQR],
      gases: ArraySeq[InfoFromTxsQR]
  ) = {
    val insByTx = inputs.groupBy(_.txHash).view.mapValues { values =>
      values
        .sortBy(_.inputOrder)
        .map(_.toApiInput())
    }
    val ousByTx = outputs.groupBy(_.txHash).view.mapValues { values =>
      values
        .sortBy(_.outputOrder)
        .map(_.toApiOutput())
    }
    val gasByTx = gases.groupBy(_.txHash).view.mapValues(_.map(_.info()))
    txHashesTs.map { txn =>
      val ins                                      = insByTx.getOrElse(txn.txHash, ArraySeq.empty)
      val ous                                      = ousByTx.getOrElse(txn.txHash, ArraySeq.empty)
      val gas                                      = gasByTx.getOrElse(txn.txHash, ArraySeq.empty)
      val (gasAmount, gasPrice, scriptExecutionOk) = gas.headOption.getOrElse((0, U256.Zero, true))
      Transaction(
        txn.txHash,
        txn.blockHash,
        txn.blockTimestamp,
        ins,
        ous,
        gasAmount,
        gasPrice,
        scriptExecutionOk,
        txn.coinbase
      )
    }
  }

  def infoFromTxs(hashes: ArraySeq[(TransactionId, BlockHash)]): DBActionSR[InfoFromTxsQR] = {
    if (hashes.nonEmpty) {
      val params = paramPlaceholderTuple2(1, hashes.size)
      val query = s"""
    SELECT hash, gas_amount, gas_price, script_execution_ok
    FROM transactions
    WHERE (hash, block_hash) IN $params
    """
      val parameters: SetParameter[Unit] =
        (_: Unit, params: PositionedParameters) =>
          hashes foreach { case (txId, blockHash) =>
            params >> txId
            params >> blockHash
          }

      SQLActionBuilder(
        queryParts = query,
        unitPConv = parameters
      ).asAS[InfoFromTxsQR]
    } else {
      DBIOAction.successful(ArraySeq.empty)
    }
  }

  private def getKnownTransactionAction(
      txHash: TransactionId,
      blockHash: BlockHash,
      timestamp: TimeStamp,
      gasAmount: Int,
      gasPrice: U256,
      scriptExecutionOk: Boolean,
      coinbase: Boolean
  )(implicit ec: ExecutionContext): DBActionR[Transaction] =
    for {
      ins  <- getInputsQuery(txHash, blockHash)
      outs <- getOutputsQuery(txHash, blockHash)
    } yield {
      Transaction(
        txHash,
        blockHash,
        timestamp,
        ins.map(_.toApiInput()),
        outs.map(_.toApiOutput()),
        gasAmount,
        gasPrice,
        scriptExecutionOk,
        coinbase
      )
    }

  def areAddressesActiveAction(
      addresses: ArraySeq[Address]
  )(implicit ec: ExecutionContext): DBActionR[ArraySeq[Boolean]] =
    filterExistingAddresses(addresses.toSet) map { existing =>
      addresses map existing.contains
    }

  /** Filters input addresses that exist in DB */
  def filterExistingAddresses(addresses: Set[Address]): DBActionR[ArraySeq[Address]] =
    if (addresses.isEmpty) {
      DBIO.successful(ArraySeq.empty)
    } else {
      val query =
        List
          .fill(addresses.size) {
            "SELECT address FROM transaction_per_addresses WHERE address = ?"
          }
          .mkString("\nUNION\n")

      val parameters: SetParameter[Unit] =
        (_: Unit, params: PositionedParameters) =>
          addresses foreach { address =>
            params >> address
          }

      SQLActionBuilder(
        queryParts = query,
        unitPConv = parameters
      ).asAS[Address]
    }

  def getBalanceAction(address: Address)(implicit ec: ExecutionContext): DBActionR[(U256, U256)] =
    getBalanceUntilLockTime(
      address = address,
      lockTime = TimeStamp.now()
    ) map { case (total, locked) =>
      (total.getOrElse(U256.Zero), locked.getOrElse(U256.Zero))
    }

  // switch logger.trace when we can disable debugging mode
  protected def debugShow(query: slickProfile.ProfileAction[_, _, _]) = {
    print(s"${query.statements.mkString}\n")
  }
}
