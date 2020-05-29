package org.alephium.explorer.web

import akka.http.scaladsl.server.Route
import sttp.tapir.server.akkahttp.AkkaHttpServerOptions

trait Server {
  implicit def serverOptions: AkkaHttpServerOptions
  def route: Route
}
