// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer

import scala.collection.immutable.ArraySeq

import org.scalacheck.Gen

import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence.model._
import org.alephium.protocol.model.{GroupIndex, TransactionId}

object Generators {

  def groupSettingGen: Gen[GroupSetting] = Gen.choose(2, 4).map(groupNum => GroupSetting(groupNum))

  def parentIndex(chainTo: GroupIndex)(implicit groupSetting: GroupSetting) =
    groupSetting.groupNum - 1 + chainTo.value

  def blockEntityToTransactions(
      block: BlockEntity,
      outputs: ArraySeq[OutputEntity]
  ): ArraySeq[Transaction] = {
    val coinbaseTxId = block.transactions.last.hash
    block.transactions.map { tx =>
      Transaction(
        tx.hash,
        block.hash,
        block.timestamp,
        block.inputs
          .filter(_.txHash === tx.hash)
          .map(input => inputEntityToApi(input, outputs.find(_.key == input.outputRefKey))),
        block.outputs.filter(_.txHash === tx.hash).map(out => outputEntityToApi(out, None)),
        tx.version,
        tx.networkId,
        tx.scriptOpt,
        tx.gasAmount,
        tx.gasPrice,
        tx.scriptExecutionOk,
        tx.inputSignatures.getOrElse(ArraySeq.empty),
        tx.scriptSignatures.getOrElse(ArraySeq.empty),
        coinbase = coinbaseTxId == tx.hash,
        conflicted = tx.conflicted
      )
    }
  }

  def blockEntitiesToBlockEntries(
      blocks: ArraySeq[ArraySeq[BlockEntity]]
  ): ArraySeq[ArraySeq[BlockEntryTest]] = {
    blocks.map(_.map { block =>
      val transactions = blockEntityToTransactions(block, blocks.flatMap(_.flatMap(_.outputs)))
      BlockEntryTest(
        block.hash,
        block.timestamp,
        block.chainFrom,
        block.chainTo,
        block.height,
        transactions,
        block.deps,
        block.nonce,
        block.version,
        block.depStateHash,
        block.txsHash,
        transactions.size,
        block.target,
        block.hashrate,
        None,
        mainChain = true,
        ghostUncles = block.ghostUncles
      )
    })
  }

  def inputEntityToApi(input: InputEntity, outputRef: Option[OutputEntity]): Input =
    Input(
      OutputRef(input.hint, input.outputRefKey),
      input.unlockScript,
      outputRef.map(_.txHash),
      outputRef.map(_.address),
      outputRef.map(_.amount),
      outputRef.flatMap(_.tokens),
      input.contractInput
    )

  def outputEntityToApi(o: OutputEntity, spent: Option[TransactionId]): Output = {
    o.outputType match {
      case OutputEntity.Asset =>
        AssetOutput(
          o.hint,
          o.key,
          o.amount,
          o.address,
          o.tokens,
          o.lockTime,
          o.message,
          spent,
          o.fixedOutput
        )
      case OutputEntity.Contract =>
        ContractOutput(o.hint, o.key, o.amount, o.address, o.tokens, spent, o.fixedOutput)
    }
  }
}
