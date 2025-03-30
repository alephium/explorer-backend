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

import org.alephium.explorer.api.model.{AssetOutput, ContractOutput, Output, Token}
import org.alephium.protocol.Hash
import org.alephium.protocol.model.{AddressLike, BlockHash, GroupIndex, TransactionId}
import org.alephium.util.{TimeStamp, U256}
import org.alephium.explorer.util.AddressUtil
trait OutputEntityLike {

  def outputType: OutputEntity.OutputType
  def hint: Int
  def key: Hash
  def amount: U256
  def address: AddressLike
  def group: Option[GroupIndex]
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
          address = AddressUtil.getAddress(address, group),
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
          address = AddressUtil.getAddress(address, group),
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
    address: AddressLike,
    group: Option[GroupIndex],
    tokens: Option[ArraySeq[Token]], // None if empty list
    mainChain: Boolean,
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
