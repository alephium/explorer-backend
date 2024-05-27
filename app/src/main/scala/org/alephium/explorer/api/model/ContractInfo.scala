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

import org.alephium.api.UtilJson.{timestampReader, timestampWriter}
import org.alephium.explorer.api.Json._
import org.alephium.json.Json._
import org.alephium.protocol.model.{Address, BlockHash, TransactionId}
import org.alephium.util.TimeStamp

final case class ContractInfo(
    parent: Option[Address],
    creationBlockHash: BlockHash,
    creationTxHash: TransactionId,
    creationTimestamp: TimeStamp,
    creationEventOrder: Int,
    destructionBlockHash: Option[BlockHash],
    destructionTxHash: Option[TransactionId],
    destructionTimestamp: Option[TimeStamp],
    destructionEventOrder: Option[Int],
    category: Option[String],
    interfaceId: Option[StdInterfaceId]
)

object ContractInfo {
  implicit val readWriter: ReadWriter[ContractInfo] = macroRW
}
