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
import org.scalacheck.Gen

import org.alephium.api.model.{Val, ValAddress, ValBool, ValByteVec}
import org.alephium.explorer.AlephiumSpec
import org.alephium.explorer.ConfigDefaults._
import org.alephium.explorer.GenApiModel._
import org.alephium.explorer.GenCoreApi._
import org.alephium.explorer.GenDBModel._
import org.alephium.explorer.persistence.model.ContractEntity
import org.alephium.protocol.model.ChainIndex

class ContractEntitySpec extends AlephiumSpec {

  val interfaceIdGen    = valByteVecGen
  val emptyByteVec: Val = ValByteVec(ByteString.empty)

  "ContractEntity.creationFromEventEntity" should {
    "return None for a random event" in {
      forAll(eventEntityGen()) { event =>
        val groupIndex = ChainIndex.from(event.blockHash).from
        ContractEntity.creationFromEventEntity(event, groupIndex) is None
      }
    }

    "convert the event for a correct create contract events" in {
      forAll(eventEntityGen(), addressGen, Gen.option(addressGen), Gen.option(interfaceIdGen)) {
        case (event, contract, parentOpt, interfaceIdOpt) =>
          val groupIndex  = ChainIndex.from(event.blockHash).from
          val parent: Val = parentOpt.map(ValAddress.apply).getOrElse(emptyByteVec)
          val createSubContractEvent = event.copy(
            contractAddress = ContractEntity.createContractEventAddress(groupIndex),
            fields = ArraySeq[Val](
              ValAddress(contract),
              parent,
              interfaceIdOpt.getOrElse(emptyByteVec)
            )
          )

          ContractEntity.creationFromEventEntity(createSubContractEvent, groupIndex) is Some(
            ContractEntity(
              contract,
              parentOpt,
              interfaceIdOpt.map(_.value),
              createSubContractEvent.blockHash,
              createSubContractEvent.txHash,
              createSubContractEvent.timestamp,
              createSubContractEvent.eventOrder,
              None,
              None,
              None,
              None,
              None,
              None,
              true
            )
          )
      }
    }

    "fail to convert the event if there isn't 3 correct fields" in {
      forAll(eventEntityGen(), addressGen, addressGen, interfaceIdGen) {
        case (event, contract, parent, interfaceId) =>
          val groupIndex = ChainIndex.from(event.blockHash).from
          val zeroField = event.copy(
            contractAddress = ContractEntity.createContractEventAddress(groupIndex),
            fields = ArraySeq.empty
          )

          ContractEntity.creationFromEventEntity(zeroField, groupIndex) is None

          val oneField = zeroField.copy(
            fields = ArraySeq(
              ValAddress(contract)
            )
          )

          ContractEntity.creationFromEventEntity(oneField, groupIndex) is None

          val twoFields = zeroField.copy(
            fields = ArraySeq(
              ValAddress(contract),
              ValAddress(parent)
            )
          )

          ContractEntity.creationFromEventEntity(twoFields, groupIndex) is None

          val fourFields = zeroField.copy(
            fields = ArraySeq[Val](
              ValAddress(contract),
              ValAddress(parent),
              interfaceId,
              ValAddress(parent)
            )
          )

          ContractEntity.creationFromEventEntity(fourFields, groupIndex) is None

          val wrongVals = zeroField.copy(
            fields = ArraySeq[Val](
              ValAddress(contract),
              ValBool(true),
              interfaceId
            )
          )

          ContractEntity.creationFromEventEntity(wrongVals, groupIndex) is None

          val wrongInterfaceId = zeroField.copy(
            fields = ArraySeq[Val](
              ValAddress(contract),
              ValBool(true),
              ValAddress(parent),
              valByteVecGen.sample.get
            )
          )

          ContractEntity.creationFromEventEntity(wrongInterfaceId, groupIndex) is None
      }
    }
  }
}
