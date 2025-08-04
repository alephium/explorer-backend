// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.api

import scala.collection.immutable.ArraySeq

import sttp.tapir._
import sttp.tapir.generic.auto._

import org.alephium.api.Endpoints.jsonBody
import org.alephium.explorer.api.EndpointExamples._
import org.alephium.explorer.api.model._
import org.alephium.protocol.model.BlockHash

trait BlockEndpoints extends BaseEndpoint with QueryParams {

  private def blocksEndpoint =
    baseEndpoint
      .tag("Blocks")
      .in("blocks")

  def getBlockByHash: BaseEndpoint[BlockHash, BlockEntry] =
    blocksEndpoint.get
      .in(path[BlockHash]("block_hash"))
      .out(jsonBody[BlockEntry])
      .summary("Get a block with hash")

  def getBlockTransactions: BaseEndpoint[(BlockHash, Pagination), ArraySeq[Transaction]] =
    blocksEndpoint.get
      .in(path[BlockHash]("block_hash"))
      .in("transactions")
      .in(pagination)
      .out(jsonBody[ArraySeq[Transaction]])
      .summary("Get block's transactions")

  def listBlocks: BaseEndpoint[Pagination.Reversible, ListBlocks] =
    blocksEndpoint.get
      .in(paginationReversible)
      .out(jsonBody[ListBlocks])
      .summary("List latest blocks")
}
