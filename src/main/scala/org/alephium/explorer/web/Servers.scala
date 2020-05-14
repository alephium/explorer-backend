package org.alephium.explorer.web

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

trait Servers extends Server {

  def blockServer: BlockServer
  def documentation: DocumentationServer

  lazy val route: Route = blockServer.route ~ documentation.route
}
