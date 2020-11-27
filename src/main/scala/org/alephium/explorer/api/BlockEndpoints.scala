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

import sttp.tapir._
import sttp.tapir.json.circe.jsonBody

import org.alephium.explorer.api.Codecs._
import org.alephium.explorer.api.Schemas._
import org.alephium.explorer.api.model.{BlockEntry, TimeInterval}
import org.alephium.util.TimeStamp

trait BlockEndpoints extends BaseEndpoint {

  private val blocksEndpoint =
    baseEndpoint
      .tag("Blocks")
      .in("blocks")

  private val timeIntervalQuery: EndpointInput[TimeInterval] =
    query[TimeStamp]("fromTs")
      .and(query[TimeStamp]("toTs"))
      .validate(
        Validator.custom({ case (from, to) => from <= to }, "`fromTs` must be before `toTs`"))
      .map({ case (from, to) => TimeInterval(from, to) })(timeInterval =>
        (timeInterval.from, timeInterval.to))

  val getBlockByHash: Endpoint[BlockEntry.Hash, ApiError, BlockEntry, Nothing] =
    blocksEndpoint.get
      .in(path[BlockEntry.Hash]("block_hash"))
      .out(jsonBody[BlockEntry])
      .description("Get a block with hash")

  val listBlocks: Endpoint[TimeInterval, ApiError, Seq[BlockEntry.Lite], Nothing] =
    blocksEndpoint.get
      .in(timeIntervalQuery)
      .out(jsonBody[Seq[BlockEntry.Lite]])
      .description("List blocks within time interval")
}
