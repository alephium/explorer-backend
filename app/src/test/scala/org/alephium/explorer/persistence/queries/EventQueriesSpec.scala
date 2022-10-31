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
import scala.concurrent.ExecutionContext

import org.scalacheck.Gen
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Minutes, Span}
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.AlephiumSpec
import org.alephium.explorer.GenApiModel._
import org.alephium.explorer.GenDBModel._
import org.alephium.explorer.Generators._
import org.alephium.explorer.persistence.{DatabaseFixtureForEach, DBRunner}
import org.alephium.explorer.persistence.model.EventEntity
import org.alephium.explorer.persistence.queries.EventQueries
import org.alephium.explorer.persistence.schema.EventSchema

class EventQueriesSpec
    extends AlephiumSpec
    with DatabaseFixtureForEach
    with DBRunner
    with ScalaFutures {

  implicit val executionContext: ExecutionContext = ExecutionContext.global
  override implicit val patienceConfig            = PatienceConfig(timeout = Span(1, Minutes))

  implicit val groupSetting = groupSettingGen.sample.get

  "Event Queries" should {
    "get event by tx hash" in new Fixture {
      forAll(Gen.nonEmptyListOf(eventEntityGen)) { events =>
        insert(events)

        events.map { event =>
          val result = run(EventQueries.getEventsByTxIdQuery(event.txHash)).futureValue
          result.size is 1
          result.head.toApi is event.toApi
        }
      }
    }

    "get all events with same tx hash" in new Fixture {
      forAll(Gen.nonEmptyListOf(eventEntityGen)) { events =>
        val txHash = transactionHashGen.sample.get
        val uniqueTxHashEvents = events.zipWithIndex.map {
          case (event, index) => event.copy(txHash = txHash, eventIndex = index)
        }

        insert(uniqueTxHashEvents)

        val result = run(EventQueries.getEventsByTxIdQuery(txHash)).futureValue

        result.size is uniqueTxHashEvents.size
        result.zip(uniqueTxHashEvents.sortBy(_.eventIndex)).map {
          case (res, event) =>
            res.toApi is event.toApi
        }
      }
    }

    "get event by contract address" in new Fixture {
      forAll(Gen.nonEmptyListOf(eventEntityGen)) { events =>
        insert(events)

        events.map { event =>
          val result =
            run(EventQueries.getEventsByContractAddressQuery(event.contractAddress)).futureValue
          result.size is 1
          result.head.toApi is event.toApi
        }
      }
    }

    "get all events with same contractAddress" in new Fixture {
      forAll(Gen.nonEmptyListOf(eventEntityGen)) { events =>
        val contractAddress = addressGen.sample.get
        val uniqueContractAddressEvents = events.map { event =>
          event.copy(contractAddress = contractAddress)
        }

        insert(uniqueContractAddressEvents)

        val result =
          run(EventQueries.getEventsByContractAddressQuery(contractAddress)).futureValue

        result.size is uniqueContractAddressEvents.size
        result.zip(uniqueContractAddressEvents.sortBy(_.timestamp).reverse).map {
          case (res, event) =>
            res.toApi is event.toApi
        }
      }
    }

    "get event by contract address and input address" in new Fixture {
      forAll(Gen.nonEmptyListOf(eventEntityGen)) { events =>
        insert(events)

        events.map { event =>
          event.inputAddress match {
            case Some(inputAddress) =>
              val result =
                run(
                  EventQueries.getEventsByContractAndInputAddressQuery(event.contractAddress,
                                                                       inputAddress)).futureValue

              result.size is 1
              result.head.toApi is event.toApi
            case None =>
              run(
                EventQueries.getEventsByContractAndInputAddressQuery(
                  event.contractAddress,
                  addressGen.sample.get)).futureValue is ArraySeq.empty

          }
        }
      }
    }

    "get all events with same contractAddress and input address" in new Fixture {
      forAll(Gen.nonEmptyListOf(eventEntityGen)) { events =>
        val contractAddress = addressGen.sample.get
        val inputAddress    = addressGen.sample.get
        val uniqueContractAddressEvents = events.map { event =>
          event.copy(contractAddress = contractAddress, inputAddress = Some(inputAddress))
        }

        insert(uniqueContractAddressEvents)

        val result =
          run(EventQueries.getEventsByContractAndInputAddressQuery(contractAddress, inputAddress)).futureValue

        result.size is uniqueContractAddressEvents.size
        result.zip(uniqueContractAddressEvents.sortBy(_.timestamp).reverse).map {
          case (res, event) =>
            res.toApi is event.toApi
        }
      }
    }
  }

  trait Fixture {
    def insert(events: ArraySeq[EventEntity]) = {
      run(EventSchema.table.delete).futureValue
      run(EventSchema.table ++= events).futureValue
    }
  }
}
