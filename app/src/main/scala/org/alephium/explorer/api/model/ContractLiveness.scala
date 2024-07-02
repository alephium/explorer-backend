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

package org.alephium.explorer.api.model

import sttp.tapir.Schema

import org.alephium.api.TapirSchemas._
import org.alephium.api.UtilJson.{timestampReader, timestampWriter}
import org.alephium.explorer.api.Json._
import org.alephium.json.Json._
import org.alephium.protocol.model.{Address, BlockHash, TransactionId}
import org.alephium.util.TimeStamp

final case class ContractLiveness(
    parent: Option[Address],
    creation: ContractLiveness.Location,
    destruction: Option[ContractLiveness.Location],
    interfaceId: Option[StdInterfaceId]
)

object ContractLiveness {

  final case class Location(
      blockHash: BlockHash,
      txHash: TransactionId,
      timestamp: TimeStamp
  )
  object Location {
    implicit val readWriter: ReadWriter[Location] = macroRW
    implicit val schema: Schema[Location] = Schema
      .derived[Location]
      .name(Schema.SName("ContractLivenessLocation"))
  }

  implicit val readWriter: ReadWriter[ContractLiveness] = macroRW
}
