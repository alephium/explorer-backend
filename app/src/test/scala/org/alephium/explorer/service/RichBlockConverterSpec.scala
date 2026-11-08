// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.service

import scala.collection.immutable.ArraySeq

import org.alephium.api.model
import org.alephium.explorer.AlephiumFutureSpec
import org.alephium.explorer.ConfigDefaults._
import org.alephium.explorer.GenCoreApi._
import org.alephium.explorer.RichAVector._
import org.alephium.explorer.RichBlockFixtures
import org.alephium.util.{AVector, U256}

class RichBlockConverterSpec extends AlephiumFutureSpec {
  private def blockWithInputs(): model.BlockEntry = {
    val block = blockEntryProtocolGen.sample.get
    block.copy(transactions =
      block.transactions.map(tx =>
        tx.copy(
          unsigned = tx.unsigned.copy(inputs = AVector(inputProtocolGen.sample.get)),
          contractInputs = AVector(outputRefProtocolGen.sample.get)
        )
      )
    )
  }

  "Rich block conversion" should {
    "preserve spent output metadata and asset/contract order without consulting other blocks" in {
      val plain     = blockWithInputs()
      val rich      = RichBlockFixtures.from(plain, ArraySeq.empty)
      val converted = RichBlockConverter.block(rich)
      val expected = rich.transactions.toArraySeq.flatMap { tx =>
        val assets: ArraySeq[model.RichInput] = tx.unsigned.inputs.toArraySeq
        assets ++ tx.contractInputs.toArraySeq
      }
      converted.inputs.zip(expected).foreach { case (input, original) =>
        input.outputRefAddress is Some(original.address)
        input.outputRefAmount is Some(original.attoAlphAmount.value)
        input.outputRefTxHash is Some(original.outputRefTxId)
        input.outputRefKey is original.key
      }
      converted.inputs.length is expected.length
      converted.outputs is BlockFlowClient.blockProtocolToEntity(plain).outputs
      converted.transactions is BlockFlowClient.blockProtocolToEntity(plain).transactions
    }

    "keep different spent amounts for identical keys in distinct blocks" in {
      val plain = blockWithInputs()
      val rich  = RichBlockFixtures.from(plain, ArraySeq.empty)
      val tx    = rich.transactions.head
      val input = tx.contractInputs.head
      val first = rich.copy(transactions =
        AVector(
          tx.copy(
            unsigned = tx.unsigned.copy(inputs = AVector.empty),
            contractInputs = AVector(input.copy(attoAlphAmount = model.Amount(U256.One)))
          )
        )
      )
      val second = first.copy(
        hash = blockEntryProtocolGen.sample.get.hash,
        transactions = first.transactions.map(t =>
          t.copy(contractInputs =
            t.contractInputs.map(_.copy(attoAlphAmount = model.Amount(U256.Two)))
          )
        )
      )
      val a = RichBlockConverter.block(first).inputs.head
      val b = RichBlockConverter.block(second).inputs.head
      a.contractInput is true
      b.contractInput is true
      a.outputRefKey is b.outputRefKey
      a.outputRefAmount is Some(U256.One)
      b.outputRefAmount is Some(U256.Two)
      (a.blockHash != b.blockHash) is true
    }
  }
}
