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

import org.alephium.api.model.ValAddress
import org.alephium.protocol
import org.alephium.protocol.model.{Address, BlockHash, TransactionId}
import org.alephium.util.TimeStamp

final case class CreateSubContractEventEntity(
    blockHash: BlockHash,
    txHash: TransactionId,
    contract: Address,
    subContract: Address,
    timestamp: TimeStamp,
    eventOrder: Int
)

object CreateSubContractEventEntity {
  val createContractEventAddress: Address =
    protocol.model.Address.contract(protocol.vm.createContractEventId)

  @SuppressWarnings(Array("org.wartremover.warts.IterableOps"))
  def fromEventEntity(event: EventEntity): Option[CreateSubContractEventEntity] = {
    if (event.contractAddress == createContractEventAddress && event.fields.sizeIs == 2) {
      (event.fields.last, event.fields.head) match {
        case (ValAddress(parent), ValAddress(subContract)) =>
          Some(
            CreateSubContractEventEntity(
              event.blockHash,
              event.txHash,
              parent,
              subContract,
              event.timestamp,
              event.eventOrder
            )
          )
        case _ => None
      }
    } else {
      None
    }
  }
}
