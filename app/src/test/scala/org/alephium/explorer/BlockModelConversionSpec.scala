// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer

import scala.collection.immutable.ArraySeq

import org.alephium.explorer.AlephiumSpec
import org.alephium.explorer.ConfigDefaults._
import org.alephium.explorer.GenCoreApi._
import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence.model._
import org.alephium.explorer.service.BlockFlowClient

class BlockModelConversionSpec() extends AlephiumSpec {

  "BlockEntry" should {
    "be converted to and from core api BlockEntry" in new Fixture {
      forAll(blockEntryProtocolGen) { protocolBlockEntry =>
        val blockEntity = BlockFlowClient.blockProtocolToEntity(protocolBlockEntry)

        blockEntityToProtocol(blockEntity) is protocolBlockEntry
      }
    }
  }

  trait Fixture {

    def blockEntityToProtocol(blockEntity: BlockEntity): org.alephium.api.model.BlockEntry = {

      val transactions = transactionsApiFromBlockEntity(blockEntity)

      blockEntity.toBlockHeader(groupSetting.groupNum).toApi().toProtocol(transactions)
    }

    def transactionsApiFromBlockEntity(
        block: BlockEntity
    ): ArraySeq[Transaction] = {
      block.transactions.map { tx =>
        tx.toApi(
          block.inputs.filter(_.txHash == tx.hash).sortBy(_.inputOrder).map(_.toApi()),
          block.outputs.filter(_.txHash == tx.hash).sortBy(_.outputOrder).map(_.toApi())
        )
      }
    }
  }
}
