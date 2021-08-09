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

package org.alephium.explorer.web

import scala.concurrent.ExecutionContext

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

import org.alephium.api.ApiError
import org.alephium.explorer.api.BlockEndpoints
import org.alephium.explorer.service.BlockService
import org.alephium.protocol.model.NetworkType
import org.alephium.util.Duration

class BlockServer(blockService: BlockService,
                  val networkType: NetworkType,
                  val blockflowFetchMaxAge: Duration)(implicit executionContext: ExecutionContext)
    extends Server
    with BlockEndpoints {
  val route: Route =
    toRoute(getBlockByHash)(
      hash =>
        blockService
          .getLiteBlockByHash(hash)
          .map(_.toRight(ApiError.NotFound(hash.value.toHexString)))) ~
      toRoute(getBlockTransactions) {
        case (hash, pagination) => blockService.getBlockTransactions(hash, pagination).map(Right(_))
      } ~
      toRoute(listBlocks)(blockService.listBlocks(_).map(Right(_)))
}
