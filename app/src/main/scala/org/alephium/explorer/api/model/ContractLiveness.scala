// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

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
