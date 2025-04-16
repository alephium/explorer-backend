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
