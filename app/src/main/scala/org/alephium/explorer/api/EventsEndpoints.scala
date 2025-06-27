// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.api

import scala.collection.immutable.ArraySeq

import sttp.tapir._
import sttp.tapir.generic.auto._

import org.alephium.api.Endpoints.jsonBody
import org.alephium.explorer.api.BaseEndpoint
import org.alephium.explorer.api.EndpointExamples._
import org.alephium.explorer.api.model._
import org.alephium.protocol.model.{Address, TransactionId}

trait EventsEndpoints extends BaseEndpoint with QueryParams {

  private val eventsEndpoint =
    baseEndpoint
      .tag("Contract events")
      .in("contract-events")

  val getEventsByTxId: BaseEndpoint[TransactionId, ArraySeq[Event]] =
    eventsEndpoint.get
      .in("transaction-id")
      .in(path[TransactionId]("transaction_id"))
      .out(jsonBody[ArraySeq[Event]])
      .summary("Get contract events by transaction id")

  val getEventsByContractAddress
      : BaseEndpoint[(Address, Option[Int], Pagination), ArraySeq[Event]] =
    eventsEndpoint.get
      .in("contract-address")
      .in(path[Address]("contract_address"))
      .in(query[Option[Int]]("event_index"))
      .in(pagination)
      .out(jsonBody[ArraySeq[Event]])
      .summary("Get contract events by contract address")

  val getEventsByContractAndInputAddress
      : BaseEndpoint[(Address, Address, Option[Int], Pagination), ArraySeq[Event]] =
    eventsEndpoint.get
      .in("contract-address")
      .in(path[Address]("contract_address"))
      .in("input-address")
      .in(path[Address]("input_address"))
      .in(query[Option[Int]]("event_index"))
      .in(pagination)
      .out(jsonBody[ArraySeq[Event]])
      .summary("Get contract events by contract and input addresses")
}
