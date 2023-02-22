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

package org.alephium.explorer.api.model

import scala.collection.immutable.ArraySeq

import org.alephium.api.UtilJson.{timestampReader, timestampWriter}
import org.alephium.explorer.api.Json._
import org.alephium.json.Json._
import org.alephium.protocol.model.{BlockHash, TransactionId}
import org.alephium.util.{TimeStamp, U256}

sealed trait TransactionLike {
  def hash: TransactionId
  def gasAmount: Int
  def gasPrice: U256
}

object TransactionLike {
  implicit val txTemplateRW: ReadWriter[TransactionLike] =
    ReadWriter.merge(AcceptedTransaction.txRW, PendingTransaction.utxRW)
}

@upickle.implicits.key("Accepted")
final case class AcceptedTransaction(
    hash: TransactionId,
    blockHash: BlockHash,
    timestamp: TimeStamp,
    inputs: ArraySeq[Input],
    outputs: ArraySeq[Output],
    gasAmount: Int,
    gasPrice: U256,
    coinbase: Boolean
) extends TransactionLike

object AcceptedTransaction {
  implicit val txRW: ReadWriter[AcceptedTransaction] = macroRW
  def from(tx: Transaction): AcceptedTransaction = AcceptedTransaction(
    tx.hash,
    tx.blockHash,
    tx.timestamp,
    tx.inputs,
    tx.outputs,
    tx.gasAmount,
    tx.gasPrice,
    tx.coinbase
  )
}

@upickle.implicits.key("Pending")
final case class PendingTransaction(
    hash: TransactionId,
    chainFrom: GroupIndex,
    chainTo: GroupIndex,
    inputs: ArraySeq[Input],
    outputs: ArraySeq[Output],
    gasAmount: Int,
    gasPrice: U256,
    lastSeen: TimeStamp
) extends TransactionLike

object PendingTransaction {
  implicit val utxRW: ReadWriter[PendingTransaction] = macroRW
  def from(mTx: MempoolTransaction): PendingTransaction =
    PendingTransaction(
      mTx.hash,
      mTx.chainFrom,
      mTx.chainTo,
      mTx.inputs,
      mTx.outputs,
      mTx.gasAmount,
      mTx.gasPrice,
      mTx.lastSeen
    )
}
