package org.alephium.explorer.web

import akka.http.scaladsl.server.Route

trait Server {
  def route: Route
}
