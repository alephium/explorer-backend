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

import akka.util.ByteString
import org.scalacheck.Gen
import org.scalatest.concurrent.ScalaFutures
import slick.jdbc.PostgresProfile.api._

import org.alephium.api.model.{Val, ValAddress, ValByteVec}
import org.alephium.explorer.AlephiumFutureSpec
import org.alephium.explorer.ConfigDefaults._
import org.alephium.explorer.GenApiModel._
import org.alephium.explorer.GenCoreApi.valByteVecGen
import org.alephium.explorer.GenDBModel._
import org.alephium.explorer.api.model.Pagination
import org.alephium.explorer.persistence.{DatabaseFixtureForEach, DBRunner}
import org.alephium.explorer.persistence.model.{ContractEntity, EventEntity}
import org.alephium.explorer.persistence.queries.ContractQueries
import org.alephium.explorer.persistence.schema.ContractSchema
import org.alephium.protocol.model.{Address, ChainIndex, GroupIndex}

@SuppressWarnings(
  Array("org.wartremover.warts.DefaultArguments", "org.wartremover.warts.AsInstanceOf")
)
class ContractQueriesSpec
    extends AlephiumFutureSpec
    with DatabaseFixtureForEach
    with DBRunner
    with ScalaFutures {

  val emptyByteVec: Val = ValByteVec(ByteString.empty)

  def createEventGen(groupIndex: GroupIndex, parentOpt: Option[Address] = None): Gen[EventEntity] =
    for {
      event       <- eventEntityGen()
      contract    <- addressGen
      interfaceId <- Gen.option(valByteVecGen)
    } yield {
      event.copy(
        contractAddress = ContractEntity.createContractEventAddress(groupIndex),
        fields = ArraySeq(
          ValAddress(contract),
          parentOpt.map(ValAddress.apply).getOrElse(emptyByteVec),
          interfaceId.getOrElse(emptyByteVec)
        )
      )
    }
  def createEventsGen(parentOpt: Option[Address] = None): Gen[(GroupIndex, Seq[EventEntity])] =
    for {
      groupIndex <- groupIndexGen
      events     <- Gen.nonEmptyListOf(createEventGen(groupIndex, parentOpt))
    } yield (groupIndex, events)

  def destroyEventGen(contract: Address): Gen[EventEntity] =
    for {
      event <- eventEntityGen()
    } yield {
      val groupIndex = ChainIndex.from(event.blockHash).from
      event.copy(
        contractAddress = ContractEntity.destroyContractEventAddress(groupIndex),
        fields = ArraySeq(
          ValAddress(contract)
        )
      )
    }

  def contractAddressFromEvent(event: EventEntity): Address = {
    event.fields.head.asInstanceOf[ValAddress].value
  }

  "Contract Queries" should {
    "insertContractCreation and updateContractDestruction" in {
      forAll(createEventsGen()) { case (groupIndex, events) =>
        // Creation
        run(ContractSchema.table.delete).futureValue
        run(ContractQueries.insertContractCreation(events, groupIndex)).futureValue
        run(ContractSchema.table.result).futureValue.sortBy(_.creationTimestamp) is events
          .flatMap(ContractEntity.creationFromEventEntity(_, groupIndex))
          .sortBy(_.creationTimestamp)

        // Destruction
        val destroyEvents = events.map(e => destroyEventGen(contractAddressFromEvent(e)).sample.get)
        run(ContractQueries.updateContractDestruction(destroyEvents, groupIndex)).futureValue

        run(ContractSchema.table.result).futureValue
          .sortBy(_.destructionTimestamp)
          .flatMap(_.destroyInfo()) is destroyEvents
          .flatMap(ContractEntity.destructionFromEventEntity(_, groupIndex))
          .sortBy(_.timestamp)
      }
    }

    "getParentAddressQuery" in {
      forAll(createEventsGen()) { case (groupIndex, events) =>
        run(ContractSchema.table.delete).futureValue
        run(ContractQueries.insertContractCreation(events, groupIndex)).futureValue

        events.flatMap(ContractEntity.creationFromEventEntity(_, groupIndex)).foreach { event =>
          run(ContractQueries.getParentAddressQuery(event.contract)).futureValue is
            event.parent
        }

        run(ContractQueries.getParentAddressQuery(addressGen.sample.get)).futureValue is None
      }
    }

    "getSubContractsQuery" in {
      val parent     = addressGen.sample.get
      val pagination = Pagination.unsafe(1, 5)

      forAll(
        createEventsGen(Some(parent)),
        createEventsGen()
      ) { case ((groupIndex, events), (otherGroup, otherEvents)) =>
        run(ContractSchema.table.delete).futureValue
        run(ContractQueries.insertContractCreation(events, groupIndex)).futureValue
        run(ContractQueries.insertContractCreation(otherEvents, otherGroup)).futureValue

        run(ContractQueries.getSubContractsQuery(parent, pagination)).futureValue is events
          .sortBy(_.timestamp)
          .reverse
          .take(pagination.limit)
          .flatMap(ContractEntity.creationFromEventEntity(_, groupIndex))
          .map(_.contract)

      }
    }
  }
}
