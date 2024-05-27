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

import akka.util.ByteString

import org.alephium.api.model.{ValAddress, ValByteVec}
import org.alephium.explorer.api.model.ContractInfo
import org.alephium.protocol
import org.alephium.protocol.model.{Address, BlockHash, GroupIndex, TransactionId}
import org.alephium.util.TimeStamp

final case class ContractEntity(
    contract: Address,
    parent: Option[Address],
    stdInterfaceIdGuessed: Option[ByteString],
    creationBlockHash: BlockHash,
    creationTxHash: TransactionId,
    creationTimestamp: TimeStamp,
    creationEventOrder: Int,
    destructionBlockHash: Option[BlockHash],
    destructionTxHash: Option[TransactionId],
    destructionTimestamp: Option[TimeStamp],
    destructionEventOrder: Option[Int],
    category: Option[String],
    interfaceId: Option[InterfaceIdEntity]
) {
  def destroyInfo(): Option[ContractEntity.DestroyInfo] =
    for {
      blockHash  <- destructionBlockHash
      txHash     <- destructionTxHash
      timestamp  <- destructionTimestamp
      eventOrder <- destructionEventOrder
    } yield ContractEntity.DestroyInfo(contract, blockHash, txHash, timestamp, eventOrder)

  def toApi: ContractInfo = ContractInfo(
    parent,
    creationBlockHash,
    creationTxHash,
    creationTimestamp,
    creationEventOrder,
    destructionBlockHash,
    destructionTxHash,
    destructionTimestamp,
    destructionEventOrder,
    category,
    interfaceId.map(_.toApi)
  )
}

object ContractEntity {

  final case class DestroyInfo(
      contract: Address,
      blockHash: BlockHash,
      txHash: TransactionId,
      timestamp: TimeStamp,
      eventOrder: Int
  )

  def createContractEventAddress(from: GroupIndex): Address.Contract = {
    protocol.model.Address.contract(
      protocol.vm.createContractEventId(from.value)
    )
  }

  def destroyContractEventAddress(from: GroupIndex): Address.Contract =
    protocol.model.Address.contract(
      protocol.vm.destroyContractEventId(from.value)
    )

  def creationFromEventEntity(event: EventEntity, from: GroupIndex): Option[ContractEntity] = {
    if (event.contractAddress == createContractEventAddress(from)) {
      extractAddresses(event).map { case (contract, parent, stdInterfaceIdGuessed) =>
        ContractEntity(
          contract = contract,
          parent = parent,
          stdInterfaceIdGuessed = stdInterfaceIdGuessed,
          creationBlockHash = event.blockHash,
          creationTxHash = event.txHash,
          creationTimestamp = event.timestamp,
          creationEventOrder = event.eventOrder,
          destructionBlockHash = None,
          destructionTxHash = None,
          destructionTimestamp = None,
          destructionEventOrder = None,
          category = None,
          interfaceId = None
        )
      }
    } else {
      None
    }
  }

  @SuppressWarnings(Array("org.wartremover.warts.IterableOps"))
  def extractAddresses(
      event: EventEntity
  ): Option[(Address, Option[Address], Option[ByteString])] = {
    if (event.fields.sizeIs == 3) {
      (event.fields(0), event.fields(1), event.fields(2)) match {
        case (ValAddress(contract), ValByteVec(ByteString.empty), ValByteVec(ByteString.empty)) =>
          Some((contract, None, None))
        case (ValAddress(contract), ValAddress(parent), ValByteVec(ByteString.empty)) =>
          Some((contract, Some(parent), None))
        case (ValAddress(contract), ValAddress(parent), ValByteVec(interfaceId)) =>
          Some((contract, Some(parent), Some(interfaceId)))
        case (ValAddress(contract), ValByteVec(ByteString.empty), ValByteVec(interfaceId)) =>
          Some((contract, None, Some(interfaceId)))
        case _ =>
          None
      }
    } else {
      None
    }
  }

  @SuppressWarnings(Array("org.wartremover.warts.IterableOps"))
  def destructionFromEventEntity(event: EventEntity, from: GroupIndex): Option[DestroyInfo] = {
    if (event.contractAddress == destroyContractEventAddress(from) && event.fields.sizeIs == 1) {
      event.fields(0) match {
        case ValAddress(contract) =>
          Some(
            DestroyInfo(contract, event.blockHash, event.txHash, event.timestamp, event.eventOrder)
          )
        case _ =>
          None
      }
    } else {
      None
    }
  }
}
