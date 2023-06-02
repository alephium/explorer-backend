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
