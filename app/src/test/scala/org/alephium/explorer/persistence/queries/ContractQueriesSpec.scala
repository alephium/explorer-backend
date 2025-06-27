// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.queries

import scala.collection.immutable.ArraySeq

import org.scalatest.concurrent.ScalaFutures
import slick.jdbc.PostgresProfile.api._

import org.alephium.api.model.ValAddress
import org.alephium.explorer.AlephiumFutureSpec
import org.alephium.explorer.ConfigDefaults._
import org.alephium.explorer.GenApiModel._
import org.alephium.explorer.GenDBModel._
import org.alephium.explorer.api.model.Pagination
import org.alephium.explorer.persistence.{DatabaseFixtureForEach, DBRunner}
import org.alephium.explorer.persistence.model.{ContractEntity, EventEntity}
import org.alephium.explorer.persistence.queries.ContractQueries
import org.alephium.explorer.persistence.schema.ContractSchema
import org.alephium.protocol.model.Address

@SuppressWarnings(
  Array("org.wartremover.warts.DefaultArguments", "org.wartremover.warts.AsInstanceOf")
)
class ContractQueriesSpec
    extends AlephiumFutureSpec
    with DatabaseFixtureForEach
    with DBRunner
    with ScalaFutures {

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

    "getContractEntity" in {
      forAll(createEventsGen()) { case (groupIndex, events) =>
        run(ContractSchema.table.delete).futureValue
        run(ContractQueries.insertContractCreation(events, groupIndex)).futureValue

        events.flatMap(ContractEntity.creationFromEventEntity(_, groupIndex)).foreach { event =>
          run(ContractQueries.getContractEntity(event.contract)).futureValue is
            ArraySeq(event)
        }

        run(ContractQueries.getContractEntity(addressGen.sample.get)).futureValue is ArraySeq.empty
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

    "getSubContractsQuery when duplicate contracts exists" in {
      val parent     = addressGen.sample.get
      val pagination = Pagination.unsafe(1, 5)

      forAll(
        createEventsGen(Some(parent)),
        createEventsGen()
      ) { case ((groupIndex, events), (otherGroup, otherEvents)) =>
        val eventsWithDuplicates = events ++ events.map(event =>
          event.copy(timestamp = event.timestamp.plusSecondsUnsafe(1))
        )

        run(ContractSchema.table.delete).futureValue
        run(ContractQueries.insertContractCreation(eventsWithDuplicates, groupIndex)).futureValue
        run(ContractQueries.insertContractCreation(otherEvents, otherGroup)).futureValue

        run(
          ContractQueries.getSubContractsQuery(parent, pagination)
        ).futureValue is eventsWithDuplicates
          .sortBy(_.timestamp)
          .reverse
          .flatMap(ContractEntity.creationFromEventEntity(_, groupIndex))
          .map(_.contract)
          .distinct
          .take(pagination.limit)

      }
    }
  }
}
