// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.service

import org.alephium.api.model
import org.alephium.explorer.GroupSetting
import org.alephium.explorer.RichAVector._
import org.alephium.explorer.api.model.Token
import org.alephium.explorer.config.ExplorerConfig.Consensus
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.util.AddressUtil
import org.alephium.protocol.model.{Address, TransactionId}

/** Preserve the node's block-specific spent-output data, including contract inputs. */
object RichBlockConverter {
  def block(entry: model.RichBlockEntry)(implicit
      groupSetting: GroupSetting,
      consensus: Consensus
  ): BlockEntity = {
    val basic = BlockFlowClient.blockProtocolToEntity(plainBlock(entry))
    val inputs = entry.transactions.toArraySeq.zipWithIndex.flatMap { case (tx, txOrder) =>
      val assets = tx.unsigned.inputs.toArraySeq.zipWithIndex.map { case (input, inputOrder) =>
        toInput(input, entry, tx.unsigned.txId, txOrder, inputOrder)
      }
      val contracts = tx.contractInputs.toArraySeq.zipWithIndex.map { case (input, inputOrder) =>
        toInput(input, entry, tx.unsigned.txId, txOrder, inputOrder + tx.unsigned.inputs.length)
      }
      assets ++ contracts
    }
    basic.copy(inputs = inputs)
  }

  def withEvents(entry: model.RichBlockAndEvents)(implicit
      groupSetting: GroupSetting,
      consensus: Consensus
  ): BlockEntityWithEvents = {
    val callers: Map[TransactionId, Option[Address]] = entry.block.transactions.iterator.map { tx =>
      val caller = tx.unsigned.inputs.headOption.map(_.address).filter { address =>
        tx.unsigned.inputs.forall(_.address == address)
      }
      tx.unsigned.txId -> caller.map(address => address: Address)
    }.toMap
    val events = entry.events.zipWithIndex.map { case (event, order) =>
      EventEntity.from(
        entry.block.hash,
        event.txId,
        event.contractAddress,
        callers.getOrElse(event.txId, None),
        entry.block.timestamp,
        event.eventIndex,
        event.fields.toArraySeq,
        order
      )
    }.toArraySeq
    BlockEntityWithEvents(block(entry.block), events)
  }

  private def toInput(
      input: model.RichInput,
      block: model.RichBlockEntry,
      txId: TransactionId,
      txOrder: Int,
      inputOrder: Int
  ): InputEntity = {
    val unlockScript = input match {
      case asset: model.RichAssetInput => Some(asset.unlockScript)
      case _: model.RichContractInput  => None
    }
    InputEntity(
      blockHash = block.hash,
      txHash = txId,
      timestamp = block.timestamp,
      hint = input.hint,
      outputRefKey = input.key,
      unlockScript = unlockScript,
      mainChain = false,
      conflicted = None,
      inputOrder = inputOrder,
      txOrder = txOrder,
      outputRefTxHash = Some(input.outputRefTxId),
      outputRefAddress = Some(input.address),
      outputRefGrouplessAddress = AddressUtil.convertToGrouplessAddress(input.address),
      outputRefAmount = Some(input.attoAlphAmount.value),
      outputRefTokens = if (input.tokens.isEmpty) {
        None
      } else {
        Some(input.tokens.map(t => Token(t.id, t.amount)).toArraySeq)
      },
      contractInput = unlockScript.isEmpty
    )
  }

  // Reuse the existing transaction, output, header and hashrate conversion unchanged.
  private def plainBlock(entry: model.RichBlockEntry): model.BlockEntry =
    model.BlockEntry(
      entry.hash,
      entry.timestamp,
      entry.chainFrom,
      entry.chainTo,
      entry.height,
      entry.deps,
      entry.transactions.map { tx =>
        val unsigned = tx.unsigned
        model.Transaction(
          model.UnsignedTx(
            unsigned.txId,
            unsigned.version,
            unsigned.networkId,
            unsigned.scriptOpt,
            unsigned.gasAmount,
            unsigned.gasPrice,
            unsigned.inputs.map(i =>
              model.AssetInput(model.OutputRef(i.hint, i.key), i.unlockScript)
            ),
            unsigned.fixedOutputs
          ),
          tx.scriptExecutionOk,
          tx.contractInputs.map(i => model.OutputRef(i.hint, i.key)),
          tx.generatedOutputs,
          tx.inputSignatures,
          tx.scriptSignatures
        )
      },
      entry.nonce,
      entry.version,
      entry.depStateHash,
      entry.txsHash,
      entry.target,
      entry.ghostUncles,
      entry.conflictedTxs
    )
}
