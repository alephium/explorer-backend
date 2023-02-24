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
import org.alephium.explorer.persistence.model.CreateSubContractEventEntity

class CreateSubContractEventEntitySpec extends AlephiumSpec {

  "CreateSubContractEventEntity.fromEventEntity" should {
    "return None for a random event" in {
      forAll(eventEntityGen) { event =>
        CreateSubContractEventEntity.fromEventEntity(event) is None
      }
    }

    "convert the event for a correct create sub contract event" in {
      forAll(eventEntityGen, addressGen, addressGen) {
        case (event, parent, subContract) =>
          val createSubContractEvent = event.copy(
            contractAddress = CreateSubContractEventEntity.createContractEventAddress,
            fields = ArraySeq(
              ValAddress(subContract),
              ValAddress(parent)
            )
          )

          CreateSubContractEventEntity.fromEventEntity(createSubContractEvent) is Some(
            CreateSubContractEventEntity(
              createSubContractEvent.blockHash,
              createSubContractEvent.txHash,
              parent,
              subContract,
              createSubContractEvent.timestamp,
              createSubContractEvent.eventOrder
            ))
      }
    }

    "fail to convert the event if there isn't exactly 2 ValAddress fields" in {
      forAll(eventEntityGen, addressGen, addressGen) {
        case (event, parent, subContract) =>
          val oneField = event.copy(
            contractAddress = CreateSubContractEventEntity.createContractEventAddress,
            fields = ArraySeq(
              ValAddress(subContract)
            )
          )

          CreateSubContractEventEntity.fromEventEntity(oneField) is None

          val threeFields = oneField.copy(
            fields = ArraySeq(
              ValAddress(subContract),
              ValAddress(parent),
              ValAddress(addressGen.sample.get)
            )
          )

          CreateSubContractEventEntity.fromEventEntity(threeFields) is None

          val wrongVals = oneField.copy(
            fields = ArraySeq[Val](
              ValAddress(subContract),
              ValBool(true)
            )
          )

          CreateSubContractEventEntity.fromEventEntity(wrongVals) is None
      }
    }
  }
}
