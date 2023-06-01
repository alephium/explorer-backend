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

package org.alephium.explorer

import java.math.BigInteger

import scala.collection.immutable.ArraySeq

import org.scalacheck.Gen

import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence.model._
import org.alephium.protocol.model.{GroupIndex, TransactionId}

object Generators {

  def groupSettingGen: Gen[GroupSetting] = Gen.choose(2, 4).map(groupNum => GroupSetting(groupNum))

  def parentIndex(chainTo: GroupIndex)(implicit groupSetting: GroupSetting) =
    groupSetting.groupNum - 1 + chainTo.value

  def blockEntitiesToBlockEntries(
      blocks: ArraySeq[ArraySeq[BlockEntity]]): ArraySeq[ArraySeq[BlockEntry]] = {
    val outputs: ArraySeq[OutputEntity] = blocks.flatMap(_.flatMap(_.outputs))

    blocks.map(_.map { block =>
      val coinbaseTxId = block.transactions.last.hash
      val transactions =
        block.transactions.map {
          tx =>
            Transaction(
              tx.hash,
              block.hash,
              block.timestamp,
              block.inputs
                .filter(_.txHash === tx.hash)
                .map(input                                       => inputEntityToApi(input, outputs.head)), //TODO Fix when we have a valid blockchain generator
              block.outputs.filter(_.txHash === tx.hash).map(out => outputEntityToApi(out, None)),
              tx.gasAmount,
              tx.gasPrice,
              tx.scriptExecutionOk,
              coinbase = coinbaseTxId == tx.hash
            )
        }
      BlockEntry(
        block.hash,
        block.timestamp,
        block.chainFrom,
        block.chainTo,
        block.height,
        block.deps,
        transactions,
        mainChain = true,
        BigInteger.ZERO
      )
    })
  }

  def inputEntityToApi(input: InputEntity, outputRef: OutputEntity): Input =
    Input(
      OutputRef(input.hint, input.outputRefKey),
      input.unlockScript,
      Some(outputRef.txHash),
      Some(outputRef.address),
      Some(outputRef.amount),
      outputRef.tokens
    )

  def outputEntityToApi(o: OutputEntity, spent: Option[TransactionId]): Output = {
    o.outputType match {
      case OutputEntity.Asset =>
        AssetOutput(o.hint, o.key, o.amount, o.address, o.tokens, o.lockTime, o.message, spent)
      case OutputEntity.Contract =>
        ContractOutput(o.hint, o.key, o.amount, o.address, o.tokens, spent)
    }
  }
}
