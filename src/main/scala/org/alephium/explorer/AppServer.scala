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

package org.alephium.explorer

import scala.concurrent.ExecutionContext

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import ch.megard.akka.http.cors.scaladsl.CorsDirectives._
import com.typesafe.scalalogging.StrictLogging

import org.alephium.explorer.service._
import org.alephium.explorer.web._

// scalastyle:off magic.number
class AppServer(blockService: BlockService, transactionService: TransactionService)(
    implicit executionContext: ExecutionContext)
    extends StrictLogging
    with AkkaDecodeFailureHandler {

  val blockServer: BlockServer             = new BlockServer(blockService)
  val addressServer: AddressServer         = new AddressServer(transactionService)
  val transactionServer: TransactionServer = new TransactionServer(transactionService)
  val documentation: DocumentationServer   = new DocumentationServer

  val route: Route =
    cors()(blockServer.route ~ addressServer.route ~ transactionServer.route ~ documentation.route)
}
