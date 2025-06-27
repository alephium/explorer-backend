// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.queries.result

import scala.collection.immutable.ArraySeq

import slick.jdbc.{GetResult, PositionedResult}

import org.alephium.explorer.persistence.schema.CustomGetResult._
import org.alephium.protocol.model.{BlockHash, TransactionId}
import org.alephium.util.TimeStamp

object TxByTokenQR {

  val selectFields: String =
    "tx_hash, block_hash, block_timestamp, tx_order"

  private type Tuple = (TransactionId, BlockHash, TimeStamp, Int, Boolean)

  implicit val transactionByAddressQRGetResult: GetResult[TxByTokenQR] =
    (result: PositionedResult) =>
      TxByTokenQR(
        txHash = result.<<,
        blockHash = result.<<,
        blockTimestamp = result.<<,
        txOrder = result.<<
      )

  @SuppressWarnings(Array("org.wartremover.warts.Overloading"))
  def apply(tuple: Tuple): TxByTokenQR =
    TxByTokenQR(
      txHash = tuple._1,
      blockHash = tuple._2,
      blockTimestamp = tuple._3,
      txOrder = tuple._4
    )

  @SuppressWarnings(Array("org.wartremover.warts.Overloading"))
  def apply(tuples: ArraySeq[Tuple]): ArraySeq[TxByTokenQR] =
    tuples map apply

}

/** Query result for
  * [[org.alephium.explorer.persistence.queries.TransactionQueries.getTransactionsByAddress]]
  */
final case class TxByTokenQR(
    txHash: TransactionId,
    blockHash: BlockHash,
    blockTimestamp: TimeStamp,
    txOrder: Int
) {

  def hashes(): (TransactionId, BlockHash) =
    (txHash, blockHash)

  def toTxByAddressQR: TxByAddressQR = TxByAddressQR(
    txHash,
    blockHash,
    blockTimestamp,
    txOrder,
    coinbase = false
  )

}
