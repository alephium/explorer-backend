// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.model

import scala.collection.immutable.ArraySeq

import akka.util.ByteString

import org.alephium.explorer.api.model.{AssetOutput, ContractOutput, Output, Token}
import org.alephium.protocol.Hash
import org.alephium.protocol.model.{Address, BlockHash, TransactionId}
import org.alephium.util.{TimeStamp, U256}
trait OutputEntityLike {

  def outputType: OutputEntity.OutputType
  def hint: Int
  def key: Hash
  def amount: U256
  def address: Address
  def grouplessAddress: Option[GrouplessAddress]
  def tokens: Option[ArraySeq[Token]]
  def lockTime: Option[TimeStamp]
  def message: Option[ByteString]
  def spentFinalized: Option[TransactionId]
  def fixedOutput: Boolean

  def toApi(): Output =
    outputType match {
      case OutputEntity.Asset =>
        AssetOutput(
          hint = hint,
          key = key,
          attoAlphAmount = amount,
          address = address,
          tokens = tokens,
          lockTime = lockTime,
          message = message,
          spent = spentFinalized,
          fixedOutput = fixedOutput
        )

      case OutputEntity.Contract =>
        ContractOutput(
          hint = hint,
          key = key,
          attoAlphAmount = amount,
          address = address,
          tokens = tokens,
          spent = spentFinalized,
          fixedOutput = fixedOutput
        )
    }
}

final case class OutputEntity(
    blockHash: BlockHash,
    txHash: TransactionId,
    timestamp: TimeStamp,
    outputType: OutputEntity.OutputType,
    hint: Int,
    key: Hash,
    amount: U256,
    address: Address,
    grouplessAddress: Option[GrouplessAddress],
    tokens: Option[ArraySeq[Token]], // None if empty list
    mainChain: Boolean,
    conflicted: Option[Boolean],
    lockTime: Option[TimeStamp],
    message: Option[ByteString],
    outputOrder: Int,
    txOrder: Int,
    coinbase: Boolean,
    spentFinalized: Option[TransactionId],
    spentTimestamp: Option[TimeStamp],
    fixedOutput: Boolean
) extends OutputEntityLike

object OutputEntity {
  sealed trait OutputType {
    def value: Int
  }
  case object Asset extends OutputType {
    val value = 0
  }
  case object Contract extends OutputType {
    val value = 1
  }

  object OutputType {
    def unsafe(int: Int): OutputType = {
      int match {
        case Asset.value    => Asset
        case Contract.value => Contract
      }
    }
  }
}
