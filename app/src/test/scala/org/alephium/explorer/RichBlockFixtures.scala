// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer

import scala.collection.immutable.ArraySeq

import org.alephium.api.model
import org.alephium.explorer.RichAVector._
import org.alephium.explorer.util.InputAddressUtil
import org.alephium.protocol.model.{Address, ContractId, TransactionId}
import org.alephium.protocol.vm.LockupScript
import org.alephium.util.{AVector, U256}

/** Adapt the existing synthetic block fixtures to the node's rich API. Coherent chains resolve
  * their inputs from the supplied outputs. Random standalone blocks and random uncle transactions
  * have no ledger; their otherwise unknown inputs use explicit zero values and deterministic
  * placeholder addresses. Correctness regressions supply their own rich input metadata instead.
  */
object RichBlockFixtures {
  // Keep this mechanical API fixture conversion together.
  // scalastyle:off method.length
  def from(
      block: model.BlockEntry,
      knownBlocks: ArraySeq[model.BlockEntry]
  ): model.RichBlockEntry = {
    val outputs = knownBlocks.flatMap(_.transactions.toArraySeq.flatMap { tx =>
      tx.unsigned.fixedOutputs.toArraySeq.map(o => (o.key, tx.unsigned.txId, o.upCast())) ++
        tx.generatedOutputs.toArraySeq.map(o => (o.key, tx.unsigned.txId, o))
    })
    val transactions = block.transactions.map { tx =>
      val inputs = InputAddressUtil.convertSameAsPrevious(tx.unsigned.inputs.toArraySeq).map { in =>
        val output = outputs.find(_._1 == in.outputRef.key)
        val address =
          output.map(_._3.address).orElse(InputAddressUtil.addressFromProtocolInput(in)) match {
            case Some(asset: Address.Asset) => asset
            case _                          => Address.Asset(LockupScript.P2PKH(in.outputRef.key))
          }
        model.RichAssetInput(
          in.outputRef.hint,
          in.outputRef.key,
          in.unlockScript,
          output.map(_._3.attoAlphAmount).getOrElse(model.Amount(U256.Zero)),
          address,
          output.map(_._3.tokens).getOrElse(AVector.empty),
          output.map(_._2).getOrElse(TransactionId.from(in.outputRef.key.bytes).get)
        )
      }
      val contracts = tx.contractInputs.map { in =>
        val output = outputs.find(_._1 == in.key)
        val address = output.map(_._3.address) match {
          case Some(contract: Address.Contract) => contract
          case _ => Address.contract(ContractId.from(in.key.bytes).get)
        }
        model.RichContractInput(
          in.hint,
          in.key,
          output.map(_._3.attoAlphAmount).getOrElse(model.Amount(U256.Zero)),
          address,
          output.map(_._3.tokens).getOrElse(AVector.empty),
          output.map(_._2).getOrElse(TransactionId.from(in.key.bytes).get)
        )
      }
      val unsigned = tx.unsigned
      model.RichTransaction(
        model.RichUnsignedTx(
          unsigned.txId,
          unsigned.version,
          unsigned.networkId,
          unsigned.scriptOpt,
          unsigned.gasAmount,
          unsigned.gasPrice,
          AVector.from(inputs),
          unsigned.fixedOutputs
        ),
        tx.scriptExecutionOk,
        contracts,
        tx.generatedOutputs,
        tx.inputSignatures,
        tx.scriptSignatures
      )
    }
    model.RichBlockEntry(
      block.hash,
      block.timestamp,
      block.chainFrom,
      block.chainTo,
      block.height,
      block.deps,
      transactions,
      block.nonce,
      block.version,
      block.depStateHash,
      block.txsHash,
      block.target,
      block.ghostUncles,
      block.conflictedTxs
    )
  }
  // scalastyle:on method.length
}
