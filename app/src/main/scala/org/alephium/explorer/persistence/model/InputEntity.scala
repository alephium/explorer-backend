// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.model

import scala.collection.immutable.ArraySeq

import akka.util.ByteString

import org.alephium.explorer.api.model.{Input, OutputRef, Token}
import org.alephium.protocol.Hash
import org.alephium.protocol.model.{Address, BlockHash, TransactionId}
import org.alephium.util.{TimeStamp, U256}

trait InputEntityLike {
  def hint: Int
  def outputRefKey: Hash
  def unlockScript: Option[ByteString]
  def outputRefTxHash: Option[TransactionId]
  def outputRefAddress: Option[Address]
  def outputRefGrouplessAddress: Option[GrouplessAddress]
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
    conflicted: Option[Boolean],
    inputOrder: Int,
    txOrder: Int,
    outputRefTxHash: Option[TransactionId],
    outputRefAddress: Option[Address],
    outputRefGrouplessAddress: Option[GrouplessAddress],
    outputRefAmount: Option[U256],
    outputRefTokens: Option[ArraySeq[Token]], // None if empty list
    contractInput: Boolean
) extends InputEntityLike {

  /** @return All hash types associated with this [[InputEntity]] */
  def hashes(): (TransactionId, BlockHash) =
    (txHash, blockHash)
}
