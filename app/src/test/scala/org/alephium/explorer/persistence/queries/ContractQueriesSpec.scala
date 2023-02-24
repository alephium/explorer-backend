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

package org.alephium.explorer.persistence.queries

import scala.collection.immutable.ArraySeq

import org.scalacheck.Gen
import org.scalatest.concurrent.ScalaFutures
import slick.jdbc.PostgresProfile.api._

import org.alephium.api.model.ValAddress
import org.alephium.explorer.AlephiumFutureSpec
import org.alephium.explorer.GenApiModel._
import org.alephium.explorer.GenDBModel._
import org.alephium.explorer.api.model.Pagination
import org.alephium.explorer.persistence.{DatabaseFixtureForEach, DBRunner}
import org.alephium.explorer.persistence.model.{CreateSubContractEventEntity, EventEntity}
import org.alephium.explorer.persistence.queries.ContractQueries
import org.alephium.explorer.persistence.schema.CreateSubContractEventSchema
import org.alephium.protocol.model.Address

@SuppressWarnings(
  Array("org.wartremover.warts.DefaultArguments", "org.wartremover.warts.AsInstanceOf"))
class ContractQueriesSpec
    extends AlephiumFutureSpec
    with DatabaseFixtureForEach
    with DBRunner
    with ScalaFutures {

  def createEventGen(contract: Option[Address] = None): Gen[EventEntity] =
    for {
      event       <- eventEntityGen
      parent      <- contract.map(Gen.const).getOrElse(addressGen)
      subContract <- addressGen
    } yield {
      event.copy(
        contractAddress = CreateSubContractEventEntity.createContractEventAddress,
        fields = ArraySeq(
          ValAddress(subContract),
          ValAddress(parent)
        )
      )
    }

  "Contract Queries" should {
    "insertSubContractCreation" in {
      forAll(Gen.nonEmptyListOf(createEventGen())) { events =>
        run(CreateSubContractEventSchema.table.delete).futureValue
        run(ContractQueries.insertSubContractCreation(events)).futureValue
        run(CreateSubContractEventSchema.table.result).futureValue.sortBy(_.timestamp) is events
          .flatMap(CreateSubContractEventEntity.fromEventEntity)
          .sortBy(_.timestamp)
      }
    }

    "getParentAddressQuery" in {
      forAll(Gen.nonEmptyListOf(createEventGen())) { events =>
        run(CreateSubContractEventSchema.table.delete).futureValue
        run(ContractQueries.insertSubContractCreation(events)).futureValue

        events.flatMap(CreateSubContractEventEntity.fromEventEntity).foreach { event =>
          run(ContractQueries.getParentAddressQuery(event.subContract)).futureValue is Some(
            event.contract)
        }

        run(ContractQueries.getParentAddressQuery(addressGen.sample.get)).futureValue is None
      }
    }

    "getSubContractsQuery" in {
      val parent     = addressGen.sample.get
      val pagination = Pagination.unsafe(1, 5)

      forAll(Gen.nonEmptyListOf(createEventGen(Some(parent))), Gen.nonEmptyListOf(createEventGen())) {
        case (events, otherEvents) =>
          run(CreateSubContractEventSchema.table.delete).futureValue
          run(ContractQueries.insertSubContractCreation(events ++ otherEvents)).futureValue

          run(ContractQueries.getSubContractsQuery(parent, pagination)).futureValue is events
            .sortBy(_.timestamp)
            .reverse
            .take(pagination.limit)
            .flatMap(CreateSubContractEventEntity.fromEventEntity)
            .map(_.subContract)

      }
    }
  }
}
