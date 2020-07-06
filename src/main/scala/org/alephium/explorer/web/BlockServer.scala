package org.alephium.explorer.web

import scala.concurrent.ExecutionContext

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import sttp.tapir.server.akkahttp.{AkkaHttpServerOptions, RichAkkaHttpEndpoint}

import org.alephium.explorer.api.{ApiError, BlockEndpoints}
import org.alephium.explorer.service.BlockService

class BlockServer(blockService: BlockService)(implicit val serverOptions: AkkaHttpServerOptions,
                                              executionContext: ExecutionContext)
    extends Server
    with BlockEndpoints {
  val route: Route =
    getBlockByHash.toRoute(hash =>
      blockService.getBlockByHash(hash).map(_.toRight(ApiError.NotFound(hash.value.toHexString)))) ~
      listBlocks.toRoute(blockService.listBlocks(_).map(Right(_)))
}
