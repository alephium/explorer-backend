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

package org.alephium.explorer.api

import scala.collection.immutable.ArraySeq

import sttp.tapir._
import sttp.tapir.generic.auto._

import org.alephium.api.{alphJsonBody => jsonBody}
import org.alephium.explorer.api.BaseEndpoint
import org.alephium.explorer.api.model._
import org.alephium.protocol.model.TransactionId

trait EventsEndpoints extends BaseEndpoint with QueryParams {

  private val eventsEndpoint =
    baseEndpoint
      .tag("Smart contract events")
      .in("smart-contract-events")

  val getEventsByTxId: BaseEndpoint[TransactionId, ArraySeq[Event]] =
    eventsEndpoint.get
      .in("transaction-id")
      .in(path[TransactionId]("transaction_id"))
      .out(jsonBody[ArraySeq[Event]])
      .description("Get contract events by transaction id")

  val getEventsByContractAddress: BaseEndpoint[Address, ArraySeq[Event]] =
    eventsEndpoint.get
      .in("contract-address")
      .in(path[Address]("contract_address"))
      .out(jsonBody[ArraySeq[Event]])
      .description("Get contract events by contract address")

  val getEventsByContractAndInputAddress: BaseEndpoint[(Address, Address), ArraySeq[Event]] =
    eventsEndpoint.get
      .in("contract-address")
      .in(path[Address]("contract_address"))
      .in("input-address")
      .in(path[Address]("input_address"))
      .out(jsonBody[ArraySeq[Event]])
      .description("Get contract events by contract address")
}
