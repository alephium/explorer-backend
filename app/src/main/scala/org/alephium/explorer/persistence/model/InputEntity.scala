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

import org.alephium.explorer.api.model.{Input, OutputRef, Token}
import org.alephium.protocol.Hash
import org.alephium.protocol.model.{Address, AddressLike, BlockHash, TransactionId}
import org.alephium.util.{TimeStamp, U256}

trait InputEntityLike {
  def hint: Int
  def outputRefKey: Hash
  def unlockScript: Option[ByteString]
  def outputRefTxHash: Option[TransactionId]
  def outputRefAddress: Option[Address]
  def outputRefAddressLike: Option[AddressLike]
  def outputRefAmount: Option[U256]
  def outputRefTokens: Option[ArraySeq[Token]]
  def contractInput: Boolean

  def toApi(): Input =
    Input(
      outputRef = OutputRef(hint, outputRefKey),
      unlockScript = unlockScript,
      txHashRef = outputRefTxHash,
      address = outputRefAddress,
      attoAlphAmount = outputRefAmount,
      tokens = outputRefTokens,
      contractInput = contractInput
    )
}

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
    outputRefAddressLike: Option[AddressLike],
    outputRefAmount: Option[U256],
    outputRefTokens: Option[ArraySeq[Token]], // None if empty list
    contractInput: Boolean
) extends InputEntityLike {

  /** @return All hash types associated with this [[InputEntity]] */
  def hashes(): (TransactionId, BlockHash) =
    (txHash, blockHash)
}
