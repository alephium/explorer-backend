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
import sttp.tapir.server.vertx.VertxFutureServerInterpreter

import org.alephium.explorer.cache.{BlockCache, MetricCache, TransactionCache}
import org.alephium.explorer.config.ExplorerConfig
import org.alephium.explorer.service._
import org.alephium.explorer.web._

// scalastyle:off magic.number parameter.number method.length
object AppServer {

  def routes(
      marketService: MarketService,
      exportTxsNumberThreshold: Int,
      streamParallelism: Int,
      maxTimeIntervals: ExplorerConfig.MaxTimeIntervals,
      marketConfig: ExplorerConfig.Market
  )(implicit
      ec: ExecutionContext,
      dc: DatabaseConfig[PostgresProfile],
      blockFlowClient: BlockFlowClient,
      blockCache: BlockCache,
      metricCache: MetricCache,
      transactionCache: TransactionCache,
      groupSetting: GroupSetting
  ): ArraySeq[Router => Route] = {

    val serverInterpreter: VertxFutureServerInterpreter = Server.interpreter()

    val blockServer = new BlockServer()
    val addressServer =
      new AddressServer(
        TransactionService,
        TokenService,
        exportTxsNumberThreshold,
        streamParallelism,
        maxTimeIntervals.amountHistory,
        maxTimeIntervals.exportTxs
      )
    val transactionServer = new TransactionServer()
    val infosServer =
      new InfosServer(TokenSupplyService, BlockService, TransactionService)
    val utilsServer: UtilsServer   = new UtilsServer()
    val chartsServer: ChartsServer = new ChartsServer(maxTimeIntervals.charts)
    val tokenServer: TokenServer   = new TokenServer(TokenService, HolderService)
    val mempoolServer              = new MempoolServer()
    val eventServer                = new EventServer()
    val contractServer             = new ContractServer()
    val marketServer               = new MarketServer(marketService)
    val metricsServer              = new MetricsServer(metricCache)
    val documentationServer = new DocumentationServer(
      maxTimeIntervals.exportTxs,
      marketConfig.currencies
    )

    ArraySeq(
      blockServer,
      addressServer,
      transactionServer,
      tokenServer,
      infosServer,
      chartsServer,
      utilsServer,
      mempoolServer,
      eventServer,
      contractServer,
      marketServer,
      documentationServer,
      metricsServer
    ).flatMap(server => server.endpointsLogic.map(serverInterpreter.route))
  }
}
