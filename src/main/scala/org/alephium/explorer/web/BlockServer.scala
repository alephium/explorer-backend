package org.alephium.explorer.web

import akka.http.scaladsl.server.Route
import sttp.tapir.server.akkahttp._

import org.alephium.explorer.api.BlockEndpoints
import org.alephium.explorer.service.BlockService

trait BlockServer extends Server with BlockEndpoints {

  def blockService: BlockService

  val route: Route = getBlockById.toRoute(blockService.getBlockById)
}
