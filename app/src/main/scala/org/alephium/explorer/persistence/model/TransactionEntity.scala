// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.model

import scala.collection.immutable.ArraySeq

import akka.util.ByteString

import org.alephium.explorer.api.model.{Input, Output, Transaction}
import org.alephium.protocol.model.{BlockHash, GroupIndex, TransactionId}
import org.alephium.util.{TimeStamp, U256}

final case class TransactionEntity(
    hash: TransactionId,
    blockHash: BlockHash,
    timestamp: TimeStamp,
    chainFrom: GroupIndex,
    chainTo: GroupIndex,
    version: Byte,
    networkId: Byte,
    scriptOpt: Option[String],
    gasAmount: Int,
    gasPrice: U256,
    order: Int,
    mainChain: Boolean,
    scriptExecutionOk: Boolean,
    inputSignatures: Option[ArraySeq[ByteString]],
    scriptSignatures: Option[ArraySeq[ByteString]],
    coinbase: Boolean
) {
  def toApi(inputs: ArraySeq[Input], outputs: ArraySeq[Output]): Transaction =
    Transaction(
      hash,
      blockHash,
      timestamp,
      inputs,
      outputs,
      version,
      networkId,
      scriptOpt,
      gasAmount,
      gasPrice,
      scriptExecutionOk,
      inputSignatures.getOrElse(ArraySeq.empty),
      scriptSignatures.getOrElse(ArraySeq.empty),
      coinbase
    )
}
