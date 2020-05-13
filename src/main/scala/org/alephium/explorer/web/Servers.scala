package org.alephium.explorer.web

import akka.http.scaladsl.server.Route

trait Servers extends Server {

  def blockServer: BlockServer

  lazy val route: Route = blockServer.route
}
