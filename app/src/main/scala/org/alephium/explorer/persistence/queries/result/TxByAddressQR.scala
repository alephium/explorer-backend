// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.queries.result

import scala.collection.immutable.ArraySeq

import slick.jdbc.{GetResult, PositionedResult}

import org.alephium.explorer.persistence.schema.CustomGetResult._
import org.alephium.protocol.model.{BlockHash, TransactionId}
import org.alephium.util.TimeStamp

object TxByAddressQR {

  val selectFields = "tx_hash, block_hash, block_timestamp, tx_order, coinbase"

  private type Tuple = (TransactionId, BlockHash, TimeStamp, Int, Boolean)

  implicit val transactionByAddressQRGetResult: GetResult[TxByAddressQR] =
    (result: PositionedResult) =>
      TxByAddressQR(
        txHash = result.<<,
        blockHash = result.<<,
        blockTimestamp = result.<<,
        txOrder = result.<<,
        coinbase = result.<<
      )

  @SuppressWarnings(Array("org.wartremover.warts.Overloading"))
  def apply(tuple: Tuple): TxByAddressQR =
    TxByAddressQR(
      txHash = tuple._1,
      blockHash = tuple._2,
      blockTimestamp = tuple._3,
      txOrder = tuple._4,
      coinbase = tuple._5
    )

  @SuppressWarnings(Array("org.wartremover.warts.Overloading"))
  def apply(tuples: ArraySeq[Tuple]): ArraySeq[TxByAddressQR] =
    tuples map apply

}

/** Query result for
  * [[org.alephium.explorer.persistence.queries.TransactionQueries.getTransactionsByAddress]]
  */
final case class TxByAddressQR(
    txHash: TransactionId,
    blockHash: BlockHash,
    blockTimestamp: TimeStamp,
    txOrder: Int,
    coinbase: Boolean
) {

  def hashes(): (TransactionId, BlockHash) =
    (txHash, blockHash)

}
