// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.persistence.queries

import scala.collection.immutable.ArraySeq

import org.scalacheck.Gen
import org.scalatest.concurrent.ScalaFutures
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.AlephiumFutureSpec
import org.alephium.explorer.ConfigDefaults._
import org.alephium.explorer.GenApiModel._
import org.alephium.explorer.GenCoreProtocol.transactionHashGen
import org.alephium.explorer.GenDBModel._
import org.alephium.explorer.api.model.Pagination
import org.alephium.explorer.persistence.{DatabaseFixtureForEach, TestDBRunner}
import org.alephium.explorer.persistence.model.EventEntity
import org.alephium.explorer.persistence.queries.EventQueries
import org.alephium.explorer.persistence.schema.EventSchema

class EventQueriesSpec
    extends AlephiumFutureSpec
    with DatabaseFixtureForEach
    with TestDBRunner
    with ScalaFutures {

  val pagination = Pagination.unsafe(1, Pagination.defaultLimit)

  "Event Queries" should {
    "get event by tx hash" in {
      forAll(Gen.nonEmptyListOf(eventEntityGen())) { events =>
        insert(events)

        events.map { event =>
          val result = exec(EventQueries.getEventsByTxIdQuery(event.txHash))
          result.size is 1
          result.head.toApi is event.toApi
        }
      }
    }

    "get all events with same tx hash" in {
      forAll(Gen.nonEmptyListOf(eventEntityGen())) { events =>
        val txHash = transactionHashGen.sample.get
        val uniqueTxHashEvents = events.zipWithIndex.map { case (event, order) =>
          event.copy(txHash = txHash, eventOrder = order)
        }

        insert(uniqueTxHashEvents)

        val result = exec(EventQueries.getEventsByTxIdQuery(txHash))

        result.size is uniqueTxHashEvents.size
        result.zip(uniqueTxHashEvents.sortBy(_.eventOrder)).map { case (res, event) =>
          res.toApi is event.toApi
        }
      }
    }

    "get event by contract address" in {
      forAll(Gen.nonEmptyListOf(eventEntityGen())) { events =>
        insert(events)

        events.map { event =>
          val result =
            exec(
              EventQueries.getEventsByContractAddressQuery(event.contractAddress, None, pagination)
            )
          result.size is 1
          result.head.toApi is event.toApi
        }
      }
    }

    "get all events with same contractAddress" in {
      forAll(Gen.nonEmptyListOf(eventEntityGen())) { events =>
        val contractAddress = addressGen.sample.get
        val uniqueContractAddressEvents = events.map { event =>
          event.copy(contractAddress = contractAddress)
        }

        insert(uniqueContractAddressEvents)

        val fullPagination = Pagination.unsafe(1, uniqueContractAddressEvents.size)

        val result =
          exec(
            EventQueries.getEventsByContractAddressQuery(contractAddress, None, fullPagination)
          )

        result.size is uniqueContractAddressEvents.size
        result.zip(uniqueContractAddressEvents.sortBy(_.timestamp).reverse).map {
          case (res, event) =>
            res.toApi is event.toApi
        }

        val paginatedResult =
          exec(
            EventQueries.getEventsByContractAddressQuery(contractAddress, None, pagination)
          )

        if (uniqueContractAddressEvents.sizeIs > pagination.limit) {
          paginatedResult.size is pagination.limit
        } else {
          paginatedResult.size is uniqueContractAddressEvents.size
        }
      }
    }

    "filter event by event index" in {
      forAll(Gen.nonEmptyListOf(eventEntityGen())) { events =>
        val events2 = events.map(event =>
          event.copy(eventOrder = event.eventOrder + 1, eventIndex = event.eventIndex + 1)
        )

        insert(events ++ events2)

        events.map { event =>
          val withoutFilter =
            exec(
              EventQueries.getEventsByContractAddressQuery(event.contractAddress, None, pagination)
            )

          withoutFilter.size is 2

          val result =
            exec(
              EventQueries.getEventsByContractAddressQuery(
                event.contractAddress,
                Some(event.eventIndex),
                pagination
              )
            )

          result.size is 1
          result.head.toApi is event.toApi
        }
      }
    }

    "get event by contract address and input address" in {
      forAll(Gen.nonEmptyListOf(eventEntityGen())) { events =>
        insert(events)

        events.map { event =>
          event.inputAddress match {
            case Some(inputAddress) =>
              val result =
                exec(
                  EventQueries.getEventsByContractAndInputAddressQuery(
                    event.contractAddress,
                    inputAddress,
                    None,
                    pagination
                  )
                )

              result.size is 1
              result.head.toApi is event.toApi
            case None =>
              exec(
                EventQueries.getEventsByContractAndInputAddressQuery(
                  event.contractAddress,
                  addressGen.sample.get,
                  None,
                  pagination
                )
              ) is ArraySeq.empty

          }
        }
      }
    }

    "get all events with same contractAddress and input address" in {
      forAll(Gen.nonEmptyListOf(eventEntityGen())) { events =>
        val contractAddress = addressGen.sample.get
        val inputAddress    = addressGen.sample.get
        val uniqueContractAddressEvents = events.map { event =>
          event.copy(contractAddress = contractAddress, inputAddress = Some(inputAddress))
        }

        insert(uniqueContractAddressEvents)

        val fullPagination = Pagination.unsafe(1, uniqueContractAddressEvents.size)

        val result =
          exec(
            EventQueries.getEventsByContractAndInputAddressQuery(
              contractAddress,
              inputAddress,
              None,
              fullPagination
            )
          )

        result.size is uniqueContractAddressEvents.size
        result.zip(uniqueContractAddressEvents.sortBy(_.timestamp).reverse).map {
          case (res, event) =>
            res.toApi is event.toApi
        }

        val paginatedResult =
          exec(
            EventQueries.getEventsByContractAndInputAddressQuery(
              contractAddress,
              inputAddress,
              None,
              pagination
            )
          )

        if (uniqueContractAddressEvents.sizeIs > pagination.limit) {
          paginatedResult.size is pagination.limit
        } else {
          paginatedResult.size is uniqueContractAddressEvents.size
        }
      }
    }
  }

  "get event by contract address, input address and filter event index" in {
    forAll(Gen.nonEmptyListOf(eventEntityGen())) { events =>
      val events2 = events.map(event =>
        event.copy(eventOrder = event.eventOrder + 1, eventIndex = event.eventIndex + 1)
      )

      insert(events ++ events2)

      events.map { event =>
        event.inputAddress match {
          case Some(inputAddress) =>
            val withoutFilter =
              exec(
                EventQueries.getEventsByContractAndInputAddressQuery(
                  event.contractAddress,
                  inputAddress,
                  None,
                  pagination
                )
              )

            withoutFilter.size is 2

            val result =
              exec(
                EventQueries.getEventsByContractAndInputAddressQuery(
                  event.contractAddress,
                  inputAddress,
                  Some(event.eventIndex),
                  pagination
                )
              )

            result.size is 1

          case None =>
            exec(
              EventQueries.getEventsByContractAndInputAddressQuery(
                event.contractAddress,
                addressGen.sample.get,
                Some(event.eventIndex),
                pagination
              )
            ) is ArraySeq.empty
        }
      }
    }
  }
  def insert(events: ArraySeq[EventEntity]) = {
    exec(EventSchema.table.delete)
    exec(EventSchema.table ++= events)
  }
}
