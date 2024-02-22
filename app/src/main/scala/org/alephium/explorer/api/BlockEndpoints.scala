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

import org.alephium.api.Endpoints.jsonBody
import org.alephium.explorer.api.EndpointExamples._
import org.alephium.explorer.api.model._
import org.alephium.protocol.config.GroupConfig
import org.alephium.protocol.model.{BlockHash, GroupIndex}

trait BlockEndpoints extends BaseEndpoint with QueryParams {
  def groupNum: Int
  implicit lazy val groupConfig: GroupConfig = new GroupConfig {
    val groups = groupNum
  }

  private val blocksEndpoint =
    baseEndpoint
      .tag("Blocks")
      .in("blocks")

  val getBlockByHash: BaseEndpoint[BlockHash, BlockEntryLite] =
    blocksEndpoint.get
      .in(path[BlockHash]("block_hash"))
      .out(jsonBody[BlockEntryLite])
      .description("Get a block with hash")

  val getBlockTransactions: BaseEndpoint[(BlockHash, Pagination), ArraySeq[Transaction]] =
    blocksEndpoint.get
      .in(path[BlockHash]("block_hash"))
      .in("transactions")
      .in(pagination)
      .out(jsonBody[ArraySeq[Transaction]])
      .description("Get block's transactions")

  lazy val listBlocks
      : BaseEndpoint[(Pagination.Reversible, Option[GroupIndex], Option[GroupIndex]), ListBlocks] =
    blocksEndpoint.get
      .in(paginationReversible)
      .in(groupIndexOpt("chainFrom"))
      .in(groupIndexOpt("chainTo"))
      .out(jsonBody[ListBlocks])
      .description("List latest blocks")

  private def groupIndexOpt(name: String)(implicit groupConfig: GroupConfig) =
    query[Option[GroupIndex]](name).validate(Validator.custom { groupIndex =>
      groupIndex match {
        case None =>
          ValidationResult.Valid
        case Some(groupIndex) =>
          if (GroupIndex.validate(groupIndex.value)) {
            ValidationResult.Valid
          } else {
            ValidationResult.Invalid(s"Invalid group index: ${groupIndex.value}")
          }
      }
    })
}
