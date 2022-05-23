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
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import org.alephium.explorer.cache.{BlockCache, TransactionCache}
import org.alephium.explorer.service._
import org.alephium.explorer.web._

// scalastyle:off magic.number
class AppServer(blockService: BlockService,
                transactionService: TransactionService,
                tokenSupplyService: TokenSupplyService,
                sanityChecker: SanityChecker)(implicit executionContext: ExecutionContext,
                                              dc: DatabaseConfig[PostgresProfile],
                                              blockCache: BlockCache,
                                              transactionCache: TransactionCache,
                                              groupSetting: GroupSetting)
    extends StrictLogging {

  val blockServer: BlockServer = new BlockServer(blockService)
  val addressServer: AddressServer =
    new AddressServer(transactionService)
  val transactionServer: TransactionServer =
    new TransactionServer(transactionService)
  val infosServer: InfosServer =
    new InfosServer(tokenSupplyService, blockService, transactionService)
  val utilsServer: UtilsServer   = new UtilsServer(sanityChecker)
  val chartsServer: ChartsServer = new ChartsServer()

  val route: Route =
    cors()(
      blockServer.route ~
        addressServer.route ~
        transactionServer.route ~
        infosServer.route ~
        chartsServer.route ~
        utilsServer.route ~
        DocumentationServer.route ~
        Metrics.route)
}
