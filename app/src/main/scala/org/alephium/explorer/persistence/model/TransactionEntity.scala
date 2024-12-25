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
