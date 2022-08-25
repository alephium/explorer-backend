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

import org.alephium.api.UtilJson.{timestampReader, timestampWriter}
import org.alephium.explorer
import org.alephium.explorer.{seqSerde, HashCompanion}
import org.alephium.explorer.api.Json.{hashReadWriter, u256ReadWriter}
import org.alephium.json.Json._
import org.alephium.serde._
import org.alephium.util.{TimeStamp, U256}

final case class Transaction(
    hash: Transaction.Hash,
    blockHash: BlockEntry.Hash,
    timestamp: TimeStamp,
    inputs: Seq[Input],
    outputs: Seq[Output],
    gasAmount: Int,
    gasPrice: U256
)

object Transaction {
  final class Hash(val value: explorer.Hash) extends AnyVal {
    override def toString(): String = value.toHexString
  }
  object Hash extends HashCompanion[explorer.Hash, Hash](new Hash(_), _.value)

  implicit val txRW: ReadWriter[Transaction] = macroRW
}

sealed trait TransactionLike {
  def hash: Transaction.Hash
  def gasAmount: Int
  def gasPrice: U256
}

object TransactionLike {
  implicit val txTemplateRW: ReadWriter[TransactionLike] =
    ReadWriter.merge(ConfirmedTransaction.txRW, UnconfirmedTransaction.utxRW)
}

@upickle.implicits.key("Confirmed")
final case class ConfirmedTransaction(
    hash: Transaction.Hash,
    blockHash: BlockEntry.Hash,
    timestamp: TimeStamp,
    inputs: Seq[Input],
    outputs: Seq[Output],
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

@upickle.implicits.key("Unconfirmed")
final case class UnconfirmedTransaction(
    hash: Transaction.Hash,
    chainFrom: GroupIndex,
    chainTo: GroupIndex,
    inputs: Seq[UInput],
    outputs: Seq[UOutput],
    gasAmount: Int,
    gasPrice: U256,
    lastSeen: TimeStamp
) extends TransactionLike

object UnconfirmedTransaction {
  implicit val utxRW: ReadWriter[UnconfirmedTransaction] = macroRW

  implicit val serde: Serde[UnconfirmedTransaction] = Serde.forProduct8(
    UnconfirmedTransaction.apply,
    utx =>
      (
        utx.hash,
        utx.chainFrom,
        utx.chainTo,
        utx.inputs,
        utx.outputs,
        utx.gasAmount,
        utx.gasPrice,
        utx.lastSeen
    )
  )
}
