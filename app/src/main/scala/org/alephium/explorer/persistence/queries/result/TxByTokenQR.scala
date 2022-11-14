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

object TxByTokenQR {

  val selectFields = "tx_hash, block_hash, block_timestamp, tx_order"

  private type Tuple = (TransactionId, BlockHash, TimeStamp, Int, Boolean)

  implicit val transactionByAddressQRGetResult: GetResult[TxByTokenQR] =
    (result: PositionedResult) =>
      TxByTokenQR(
        txHash         = result.<<,
        blockHash      = result.<<,
        blockTimestamp = result.<<,
        txOrder        = result.<<
    )

  @SuppressWarnings(Array("org.wartremover.warts.Overloading"))
  def apply(tuple: Tuple): TxByTokenQR =
    TxByTokenQR(
      txHash         = tuple._1,
      blockHash      = tuple._2,
      blockTimestamp = tuple._3,
      txOrder        = tuple._4
    )

  @SuppressWarnings(Array("org.wartremover.warts.Overloading"))
  def apply(tuples: ArraySeq[Tuple]): ArraySeq[TxByTokenQR] =
    tuples map apply

}

/** Query result for [[org.alephium.explorer.persistence.queries.TransactionQueries.getTransactionsByAddressNoJoin]] */
final case class TxByTokenQR(txHash: TransactionId,
                             blockHash: BlockHash,
                             blockTimestamp: TimeStamp,
                             txOrder: Int) {

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
