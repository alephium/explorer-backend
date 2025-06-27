// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.web

import scala.collection.immutable.ArraySeq
import scala.concurrent.{ExecutionContext, Future}

import io.vertx.ext.web._
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import org.alephium.api.ApiError
import org.alephium.explorer.api.BlockEndpoints
import org.alephium.explorer.cache.BlockCache
import org.alephium.explorer.service.BlockService

class BlockServer(implicit
    val executionContext: ExecutionContext,
    dc: DatabaseConfig[PostgresProfile],
    blockCache: BlockCache
) extends Server
    with BlockEndpoints {
  val routes: ArraySeq[Router => Route] =
    ArraySeq(
      route(listBlocks.serverLogicSuccess[Future](BlockService.listBlocks(_))),
      route(getBlockByHash.serverLogic[Future] { hash =>
        BlockService
          .getBlockByHash(hash)
          .map(_.toRight(ApiError.NotFound(hash.value.toHexString)))
      }),
      route(getBlockTransactions.serverLogicSuccess[Future] { case (hash, pagination) =>
        BlockService.getBlockTransactions(hash, pagination)
      })
    )
}
