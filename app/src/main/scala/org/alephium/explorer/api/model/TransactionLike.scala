// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.api.model

import scala.collection.immutable.ArraySeq

import akka.util.ByteString

import org.alephium.api.UtilJson._
import org.alephium.explorer.api.Json._
import org.alephium.json.Json._
import org.alephium.protocol.model.{BlockHash, GroupIndex, TransactionId}
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
    version: Byte,
    networkId: Byte,
    scriptOpt: Option[String],
    gasAmount: Int,
    gasPrice: U256,
    scriptExecutionOk: Boolean,
    inputSignatures: ArraySeq[ByteString],
    scriptSignatures: ArraySeq[ByteString],
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
    tx.version,
    tx.networkId,
    tx.scriptOpt,
    tx.gasAmount,
    tx.gasPrice,
    tx.scriptExecutionOk,
    tx.inputSignatures,
    tx.scriptSignatures,
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
