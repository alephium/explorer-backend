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

import org.alephium.api.model.{Val, ValAddress, ValBool}
import org.alephium.explorer.AlephiumSpec
import org.alephium.explorer.GenApiModel._
import org.alephium.explorer.GenDBModel._
import org.alephium.explorer.persistence.model.ContractEntity

class ContractEntitySpec extends AlephiumSpec {

  "ContractEntity.creationFromEventEntity" should {
    "return None for a random event" in {
      forAll(eventEntityGen) { event =>
        ContractEntity.creationFromEventEntity(event) is None
      }
    }

    "convert the event for a correct create sub contract event" in {
      forAll(eventEntityGen, addressGen, addressGen) {
        case (event, contract, parent) =>
          val createSubContractEvent = event.copy(
            contractAddress = ContractEntity.createContractEventAddress,
            fields = ArraySeq(
              ValAddress(contract),
              ValAddress(parent)
            )
          )

          ContractEntity.creationFromEventEntity(createSubContractEvent) is Some(
            ContractEntity(
              contract,
              Some(parent),
              createSubContractEvent.blockHash,
              createSubContractEvent.txHash,
              createSubContractEvent.timestamp,
              createSubContractEvent.eventOrder,
              None,
              None,
              None,
              None
            ))
      }
    }

    "convert the event for a correct creation without sub contract " in {
      forAll(eventEntityGen, addressGen) {
        case (event, contract) =>
          val createContractEvent = event.copy(
            contractAddress = ContractEntity.createContractEventAddress,
            fields = ArraySeq(
              ValAddress(contract)
            )
          )

          ContractEntity.creationFromEventEntity(createContractEvent) is Some(
            ContractEntity(
              contract,
              None,
              createContractEvent.blockHash,
              createContractEvent.txHash,
              createContractEvent.timestamp,
              createContractEvent.eventOrder,
              None,
              None,
              None,
              None
            ))
      }
    }

    "fail to convert the event if there isn't 1 or 2 ValAddress fields" in {
      forAll(eventEntityGen, addressGen, addressGen) {
        case (event, contract, parent) =>
          val zeroField = event.copy(
            contractAddress = ContractEntity.createContractEventAddress,
            fields          = ArraySeq.empty
          )

          ContractEntity.creationFromEventEntity(zeroField) is None

          val threeFields = zeroField.copy(
            fields = ArraySeq(
              ValAddress(contract),
              ValAddress(parent),
              ValAddress(addressGen.sample.get)
            )
          )

          ContractEntity.creationFromEventEntity(threeFields) is None

          val wrongVals = zeroField.copy(
            fields = ArraySeq[Val](
              ValAddress(contract),
              ValBool(true)
            )
          )

          ContractEntity.creationFromEventEntity(wrongVals) is None
      }
    }
  }
}
