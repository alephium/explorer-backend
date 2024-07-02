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

import slick.jdbc.{PositionedParameters, SetParameter, SQLActionBuilder}
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence._
import org.alephium.explorer.persistence.model.EventEntity
import org.alephium.explorer.persistence.schema.CustomGetResult._
import org.alephium.explorer.persistence.schema.CustomSetParameter._
import org.alephium.explorer.util.SlickUtil._
import org.alephium.protocol.model.{Address, TransactionId}

object EventQueries {

  def insertEventsQuery(events: Iterable[EventEntity]): DBActionW[Int] = {
    QuerySplitter.splitUpdates(rows = events, columnsPerRow = 9) { (events, placeholder) =>
      val query =
        s"""
           INSERT INTO events (
             "block_hash",
             "tx_hash",
             "contract_address",
             "input_address",
             "block_timestamp",
             "event_index",
             "fields",
             "event_order_in_block",
             "main_chain"
           )
           VALUES $placeholder
           ON CONFLICT
            ON CONSTRAINT events_pk
            DO NOTHING
           """.stripMargin

      val parameters: SetParameter[Unit] =
        (_: Unit, params: PositionedParameters) =>
          events foreach { event =>
            params >> event.blockHash
            params >> event.txHash
            params >> event.contractAddress
            params >> event.inputAddress
            params >> event.timestamp
            params >> event.eventIndex
            params >> event.fields
            params >> event.eventOrder
            params >> event.mainChain
          }

      // Return builder generated by Slick's string interpolation
      SQLActionBuilder(
        sql = query,
        setParameter = parameters
      ).asUpdate
    }
  }

  def getEventsByTxIdQuery(txId: TransactionId): DBActionSR[EventEntity] =
    sql"""
      SELECT *
      FROM events
      WHERE tx_hash = $txId
      AND main_chain = true
      ORDER BY event_order_in_block
      """.asASE[EventEntity](eventGetResult)

  def getEventsByContractAddressQuery(
      address: Address,
      pagination: Pagination
  ): DBActionSR[EventEntity] = {
    sql"""
      SELECT *
      FROM events
      WHERE contract_address = $address
      AND main_chain = true
      ORDER BY block_timestamp DESC, event_order_in_block
      """
      .paginate(pagination)
      .asASE[EventEntity](eventGetResult)
  }

  def getEventsByContractAndInputAddressQuery(
      contract: Address,
      input: Address,
      pagination: Pagination
  ): DBActionSR[EventEntity] = {
    sql"""
      SELECT *
      FROM events
      WHERE contract_address = $contract
      AND input_address = $input
      AND main_chain = true
      ORDER BY block_timestamp DESC
      """
      .paginate(pagination)
      .asASE[EventEntity](eventGetResult)
  }
}
