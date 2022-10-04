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

final case class Transaction(
    hash: TransactionId,
    blockHash: BlockHash,
    timestamp: TimeStamp,
    inputs: ArraySeq[Input],
    outputs: ArraySeq[Output],
    gasAmount: Int,
    gasPrice: U256
)

object Transaction {
  implicit val txRW: ReadWriter[Transaction] = macroRW
}

sealed trait TransactionLike {
  def hash: TransactionId
  def gasAmount: Int
  def gasPrice: U256
}

object TransactionLike {
  implicit val txTemplateRW: ReadWriter[TransactionLike] =
    ReadWriter.merge(ConfirmedTransaction.txRW, UnconfirmedTransaction.utxRW)
}

@SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
@upickle.implicits.key("Confirmed")
final case class ConfirmedTransaction(
    hash: TransactionId,
    blockHash: BlockHash,
    timestamp: TimeStamp,
    inputs: ArraySeq[Input],
    outputs: ArraySeq[Output],
    gasAmount: Int,
    gasPrice: U256
) extends TransactionLike

object ConfirmedTransaction {
  implicit val txRW: ReadWriter[ConfirmedTransaction] = macroRW
  def from(tx: Transaction): ConfirmedTransaction = ConfirmedTransaction(
    tx.hash,
    tx.blockHash,
    tx.timestamp,
    tx.inputs,
    tx.outputs,
    tx.gasAmount,
    tx.gasPrice
  )
}

@SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
@upickle.implicits.key("Unconfirmed")
final case class UnconfirmedTransaction(
    hash: TransactionId,
    chainFrom: GroupIndex,
    chainTo: GroupIndex,
    inputs: ArraySeq[Input],
    outputs: ArraySeq[Output],
    gasAmount: Int,
    gasPrice: U256,
    lastSeen: TimeStamp
) extends TransactionLike

object UnconfirmedTransaction {
  implicit val utxRW: ReadWriter[UnconfirmedTransaction] = macroRW
}
