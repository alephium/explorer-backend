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
import org.alephium.explorer.persistence.schema.CustomSetParameter._
import org.alephium.explorer.util.SlickUtil._
import org.alephium.protocol.ALPH
import org.alephium.protocol.model.{Address, AddressLike, BlockHash, TransactionId}
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
  // scalastyle:off magic.number method.length
  def insertTransactions(transactions: Iterable[TransactionEntity]): DBActionW[Int] =
    QuerySplitter.splitUpdates(rows = transactions, columnsPerRow = 16) {
      (transactions, placeholder) =>
        val query =
          s"""
             insert into transactions (hash,
                                       block_hash,
                                       block_timestamp,
                                       chain_from,
                                       chain_to,
                                       version,
                                       network_id,
                                       script_opt,
                                       gas_amount,
                                       gas_price,
                                       tx_order,
                                       main_chain,
                                       script_execution_ok,
                                       input_signatures,
                                       script_signatures,
                                       coinbase)
             values $placeholder
             ON CONFLICT ON CONSTRAINT txs_pk
                 DO NOTHING
             """

        val parameters: SetParameter[Unit] =
          (_: Unit, params: PositionedParameters) =>
            transactions foreach { transaction =>
              params >> transaction.hash
              params >> transaction.blockHash
              params >> transaction.timestamp
              params >> transaction.chainFrom
              params >> transaction.chainTo
              params >> transaction.version
              params >> transaction.networkId
              params >> transaction.scriptOpt
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
          sql = query,
          setParameter = parameters
        ).asUpdate
    }

  private def getTransactionQuery(txHash: TransactionId) =
    sql"""
      SELECT *
      FROM transactions
      WHERE hash = $txHash
      AND main_chain = true
      LIMIT 1
    """.asASE[TransactionEntity](transactionEntityGetResult).headOption

  def getTransactionAction(
      txHash: TransactionId
  )(implicit ec: ExecutionContext): DBActionR[Option[Transaction]] =
    getTransactionQuery(txHash).flatMap {
      case Some(tx) => getKnownTransactionAction(tx).map(Some.apply)
      case None     => DBIOAction.successful(None)
    }

  private def getTxHashesByBlockHashQuery(
      blockHash: BlockHash
  ): DBActionSR[TxByAddressQR] =
    sql"""
      SELECT hash, block_hash, block_timestamp, tx_order, coinbase
      FROM transactions
      WHERE block_hash = $blockHash
      ORDER BY tx_order
    """.asAS[TxByAddressQR]

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
      .asAS[TxByAddressQR]

  def countAddressTransactions(
      address: AddressLike
  ): DBActionSR[Int] = {
    sql"""
      SELECT COUNT(*)
      FROM transaction_per_addresses
      WHERE main_chain = true
      AND #${addressColumn(address, "address", "address_like")} = $address
    """
      .asAS[Int]
  }

  def getTxHashesByAddressQuery(
      address: AddressLike,
      pagination: Pagination
  ): DBActionSR[TxByAddressQR] = {
    sql"""
      SELECT #${TxByAddressQR.selectFields}
      FROM transaction_per_addresses
      WHERE main_chain = true
      AND #${addressColumn(address, "address", "address_like")} = $address
    """
      .concat(sql"""ORDER BY block_timestamp DESC, tx_order """)
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
    * @param fromTs
    *   From TimeStamp of the time-range (inclusive)
    * @param toTs
    *   To TimeStamp of the time-range (exclusive)
    * @return
    *   Paginated transactions
    */
  def getTxHashesByAddressesQuery(
      addresses: ArraySeq[Address],
      fromTs: Option[TimeStamp],
      toTs: Option[TimeStamp],
      pagination: Pagination
  ): DBActionSR[TxByAddressQR] =
    if (addresses.isEmpty) {
      DBIOAction.successful(ArraySeq.empty)
    } else {
      val placeholder = paramPlaceholder(1, addresses.size)

      val query =
        s"""
           SELECT ${TxByAddressQR.selectFields}
           FROM transaction_per_addresses
           WHERE main_chain = true
             AND address IN $placeholder
             ${fromTs.map(ts => s"AND block_timestamp >= ${ts.millis}").getOrElse("")}
             ${toTs.map(ts => s"AND block_timestamp < ${ts.millis}").getOrElse("")}
           ORDER BY block_timestamp DESC, tx_order
           """

      val parameters: SetParameter[Unit] =
        (_: Unit, params: PositionedParameters) => {
          addresses foreach (params >> _)
        }

      SQLActionBuilder(
        sql = query,
        setParameter = parameters
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
      txs        <- getTransactions(txHashesTs)
    } yield txs
  }

  def getTransactionsByBlockHashWithPagination(blockHash: BlockHash, pagination: Pagination)(
      implicit ec: ExecutionContext
  ): DBActionR[ArraySeq[Transaction]] = {
    for {
      txHashesTs <- getTxHashesByBlockHashWithPaginationQuery(blockHash, pagination)
      txs        <- getTransactions(txHashesTs)
    } yield txs
  }

  def getTransactionsByAddress(
      address: AddressLike,
      pagination: Pagination
  )(implicit
      ec: ExecutionContext
  ): DBActionR[ArraySeq[Transaction]] = {
    for {
      txHashesTs <- getTxHashesByAddressQuery(address, pagination)
      txs        <- getTransactions(txHashesTs)
    } yield txs
  }

  def getTransactionsByAddresses(
      addresses: ArraySeq[Address],
      fromTime: Option[TimeStamp],
      toTime: Option[TimeStamp],
      pagination: Pagination
  )(implicit
      ec: ExecutionContext
  ): DBActionR[ArraySeq[Transaction]] = {
    for {
      txHashesTs <- getTxHashesByAddressesQuery(addresses, fromTime, toTime, pagination)
      txs        <- getTransactions(txHashesTs)
    } yield txs
  }

  def getLatestTransactionInfoByAddressAction(
      address: Address
  )(implicit ec: ExecutionContext): DBActionR[Option[TxByAddressQR]] = {
    sql"""
      SELECT #${TxByAddressQR.selectFields}
      FROM transaction_per_addresses
      WHERE main_chain = true AND address = $address
      ORDER BY block_timestamp DESC, tx_order
      LIMIT 1
    """
      .asAS[TxByAddressQR]
      .headOrNone
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

  /*
   * Sum outputs and group them by the given interval type
   * LEAST and GREATEST are here to restrict to the `from` and `to` timestamp
   */
  def sumAddressOutputs(
      address: Address,
      from: TimeStamp,
      to: TimeStamp,
      intervalType: IntervalType
  ): DBActionSR[(TimeStamp, Option[U256])] = {
    val dateGroup = QueryUtil.dateGroupQuery(intervalType)
    sql"""
      SELECT
        LEAST($to, GREATEST($from, #${QueryUtil.extractEpoch(dateGroup)} - 1)) as ts,
        SUM(amount)
      FROM outputs
      WHERE address = $address
      AND main_chain = true
      AND block_timestamp >= ${ALPH.GenesisTimestamp}
      AND block_timestamp <= $to
      GROUP BY ts
      """.asAS[(TimeStamp, Option[U256])]
  }

  def sumAddressOutputsDEPRECATED(address: Address, from: TimeStamp, to: TimeStamp)(implicit
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

  def sumAddressInputsDEPRECATED(address: Address, from: TimeStamp, to: TimeStamp)(implicit
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

  /*
   * Sum inputs and group them by the given interval type
   * LEAST and GREATEST are here to restrict to the `from` and `to` timestamp
   */
  def sumAddressInputs(
      address: Address,
      from: TimeStamp,
      to: TimeStamp,
      intervalType: IntervalType
  ): DBActionSR[(TimeStamp, Option[U256])] = {
    val dateGroup = QueryUtil.dateGroupQuery(intervalType)

    sql"""
      SELECT
        LEAST($to, GREATEST($from, #${QueryUtil.extractEpoch(dateGroup)} - 1)) as ts,
        SUM(output_ref_amount)
      FROM inputs
      WHERE output_ref_address = $address
      AND main_chain = true
      AND block_timestamp >= ${ALPH.GenesisTimestamp}
      AND block_timestamp <= $to
      GROUP BY ts
      """.asAS[(TimeStamp, Option[U256])]
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
        .map(_.toApi())
    }
    val ousByTx = outputs.groupBy(_.txHash).view.mapValues { values =>
      values
        .sortBy(_.outputOrder)
        .map(_.toApi())
    }
    val gasByTx = gases.groupBy(_.txHash)
    txHashesTs.map { txn =>
      val ins  = insByTx.getOrElse(txn.txHash, ArraySeq.empty)
      val ous  = ousByTx.getOrElse(txn.txHash, ArraySeq.empty)
      val gas  = gasByTx.getOrElse(txn.txHash, ArraySeq.empty)
      val info = gas.headOption.getOrElse(InfoFromTxsQR.empty())
      Transaction(
        txn.txHash,
        txn.blockHash,
        txn.blockTimestamp,
        ins,
        ous,
        info.version,
        info.networkId,
        info.scriptOpt,
        info.gasAmount,
        info.gasPrice,
        info.scriptExecutionOk,
        info.inputSignatures.getOrElse(ArraySeq.empty),
        info.scriptSignatures.getOrElse(ArraySeq.empty),
        txn.coinbase
      )
    }
  }

  def infoFromTxs(hashes: ArraySeq[(TransactionId, BlockHash)]): DBActionSR[InfoFromTxsQR] = {
    if (hashes.nonEmpty) {
      val params = paramPlaceholderTuple2(1, hashes.size)
      val query = s"""
        SELECT ${InfoFromTxsQR.selectFields}
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
        sql = query,
        setParameter = parameters
      ).asAS[InfoFromTxsQR]
    } else {
      DBIOAction.successful(ArraySeq.empty)
    }
  }

  private def getKnownTransactionAction(
      tx: TransactionEntity
  )(implicit ec: ExecutionContext): DBActionR[Transaction] =
    for {
      ins  <- getInputsQuery(tx.hash, tx.blockHash)
      outs <- getOutputsQuery(tx.hash, tx.blockHash)
    } yield {
      Transaction(
        tx.hash,
        tx.blockHash,
        tx.timestamp,
        ins.map(_.toApi()),
        outs.map(_.toApi()),
        tx.version,
        tx.networkId,
        tx.scriptOpt,
        tx.gasAmount,
        tx.gasPrice,
        tx.scriptExecutionOk,
        tx.inputSignatures.getOrElse(ArraySeq.empty),
        tx.scriptSignatures.getOrElse(ArraySeq.empty),
        tx.coinbase
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
        sql = query,
        setParameter = parameters
      ).asAS[Address]
    }

  def getBalanceAction(
      address: AddressLike,
      latestFinalizedTimestamp: TimeStamp
  )(implicit ec: ExecutionContext): DBActionR[(U256, U256)] =
    getBalanceUntilLockTime(
      address = address,
      lockTime = TimeStamp.now(),
      latestFinalizedTimestamp = latestFinalizedTimestamp
    ) map { case (total, locked) =>
      (total.getOrElse(U256.Zero), locked.getOrElse(U256.Zero))
    }

  // switch logger.trace when we can disable debugging mode
  protected def debugShow(query: slickProfile.ProfileAction[_, _, _]) = {
    print(s"${query.statements.mkString}\n")
  }
}
