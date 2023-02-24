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

import scala.concurrent.ExecutionContext

import slick.jdbc.{PositionedParameters, SetParameter, SQLActionBuilder}
import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.api.model.Pagination
import org.alephium.explorer.persistence._
import org.alephium.explorer.persistence.model.{CreateSubContractEventEntity, EventEntity}
import org.alephium.explorer.persistence.schema.CustomGetResult._
import org.alephium.explorer.persistence.schema.CustomSetParameter._
import org.alephium.explorer.util.SlickUtil._
import org.alephium.protocol.model.Address

object ContractQueries {
  def insertSubContractCreation(events: Iterable[EventEntity]): DBActionW[Int] = {
    insertCreateSubContractEventEntities(
      events.flatMap(CreateSubContractEventEntity.fromEventEntity)
    )
  }

  private def insertCreateSubContractEventEntities(
      events: Iterable[CreateSubContractEventEntity]): DBActionW[Int] = {
    QuerySplitter.splitUpdates(rows = events, columnsPerRow = 6) { (events, placeholder) =>
      val query =
        s"""
           |INSERT INTO create_sub_contract_events ("block_hash", "tx_hash", "contract", "sub_contract","block_timestamp", "event_order_in_block")
           |VALUES $placeholder
           |ON CONFLICT
           | ON CONSTRAINT create_sub_contract_events_pk
           | DO NOTHING
           |""".stripMargin

      val parameters: SetParameter[Unit] =
        (_: Unit, params: PositionedParameters) =>
          events foreach { event =>
            params >> event.blockHash
            params >> event.txHash
            params >> event.contract
            params >> event.subContract
            params >> event.timestamp
            params >> event.eventOrder
        }

      SQLActionBuilder(
        queryParts = query,
        unitPConv  = parameters
      ).asUpdate
    }
  }

  def getParentAddressQuery(subContract: Address)(
      implicit ec: ExecutionContext): DBActionR[Option[Address]] = {
    sql"""
      SELECT contract
      FROM create_sub_contract_events
      WHERE sub_contract = $subContract
      LIMIT 1
      """.asASE[Address](addressGetResult).headOrNone
  }

  def getSubContractsQuery(contract: Address, pagination: Pagination): DBActionSR[Address] = {
    sql"""
      SELECT sub_contract
      FROM create_sub_contract_events
      WHERE contract = $contract
      ORDER BY block_timestamp DESC, event_order_in_block
      #${pagination.query}
      """.asASE[Address](addressGetResult)
  }
}
