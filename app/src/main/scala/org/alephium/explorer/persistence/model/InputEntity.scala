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

import org.alephium.explorer.Hash
import org.alephium.explorer.api.model._
import org.alephium.protocol.model.{Address, BlockHash, TransactionId}
import org.alephium.util.{TimeStamp, U256}

final case class InputEntity(
    blockHash: BlockHash,
    txHash: TransactionId,
    timestamp: TimeStamp,
    hint: Int,
    outputRefKey: Hash,
    unlockScript: Option[ByteString],
    mainChain: Boolean,
    inputOrder: Int,
    txOrder: Int,
    outputRefTxHash: Option[TransactionId],
    outputRefAddress: Option[Address],
    outputRefAmount: Option[U256],
    outputRefTokens: Option[ArraySeq[Token]] //None if empty list
) {
  def toApi(outputRef: OutputEntity): Input =
    Input(
      OutputRef(hint, outputRefKey),
      unlockScript,
      Some(outputRef.txHash),
      Some(outputRef.address),
      Some(outputRef.amount),
      outputRef.tokens
    )

  /** @return All hash types associated with this [[InputEntity]] */
  def hashes(): (TransactionId, BlockHash) =
    (txHash, blockHash)
}
