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
import org.alephium.util.Duration

// scalastyle:off magic.number
class AppServer(blockService: BlockService,
                transactionService: TransactionService,
                tokenSupplyService: TokenSupplyService,
                sanityChecker: SanityChecker,
                blockFlowFetchMaxAge: Duration)(implicit executionContext: ExecutionContext)
    extends StrictLogging {

  val blockServer: BlockServer = new BlockServer(blockService, blockFlowFetchMaxAge)
  val addressServer: AddressServer =
    new AddressServer(transactionService, blockFlowFetchMaxAge)
  val transactionServer: TransactionServer =
    new TransactionServer(transactionService, blockFlowFetchMaxAge)
  val infosServer: InfosServer = new InfosServer(blockFlowFetchMaxAge, tokenSupplyService)
  val utilsServer: UtilsServer = new UtilsServer(blockFlowFetchMaxAge, sanityChecker)
  val documentation: DocumentationServer =
    new DocumentationServer(blockFlowFetchMaxAge)

  val route: Route =
    cors()(
      blockServer.route ~ addressServer.route ~ transactionServer.route ~ infosServer.route ~ utilsServer.route ~ documentation.route ~ Metrics.route)
}
