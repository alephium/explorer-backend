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

import scala.collection.immutable.ArraySeq
import scala.concurrent.ExecutionContext

import io.vertx.ext.web._
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import org.alephium.explorer.cache.{BlockCache, TransactionCache}
import org.alephium.explorer.service._
import org.alephium.explorer.web._

// scalastyle:off magic.number
object AppServer {

  def routes(exportTxsNumberThreshold: Int, streamParallelism: Int)(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      blockFlowClient: BlockFlowClient,
      blockCache: BlockCache,
      transactionCache: TransactionCache,
      groupSetting: GroupSetting
  ): ArraySeq[Router => Route] = {

    val marketService = MarketService.CoinGecko.default()
    val blockServer   = new BlockServer()
    val addressServer =
      new AddressServer(
        TransactionService,
        TokenService,
        exportTxsNumberThreshold,
        streamParallelism
      )
    val transactionServer = new TransactionServer()
    val infosServer       = new InfosServer(TokenSupplyService, BlockService, TransactionService)
    val utilsServer: UtilsServer   = new UtilsServer()
    val chartsServer: ChartsServer = new ChartsServer()
    val tokenServer: TokenServer   = new TokenServer(TokenService)
    val mempoolServer              = new MempoolServer()
    val eventServer                = new EventServer()
    val contractServer             = new ContractServer()
    val marketServer               = new MarketServer(marketService)
    val documentationServer        = new DocumentationServer()

    blockServer.routes ++
      addressServer.routes ++
      transactionServer.routes ++
      tokenServer.routes ++
      infosServer.routes ++
      chartsServer.routes ++
      utilsServer.routes ++
      mempoolServer.routes ++
      eventServer.routes ++
      contractServer.routes ++
      marketServer.routes ++
      documentationServer.routes :+
      Metrics.route
  }
}
