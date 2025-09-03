// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer

import scala.collection.immutable.ArraySeq
import scala.concurrent.ExecutionContext

import io.vertx.ext.web._
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import org.alephium.explorer.cache._
import org.alephium.explorer.config.ExplorerConfig
import org.alephium.explorer.service._
import org.alephium.explorer.web._

// scalastyle:off magic.number parameter.number method.length
object AppServer {

  def routes(
      marketService: market.MarketService,
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
      addressTxCountCache: AddressTxCountCache,
      groupSetting: GroupSetting
  ): ArraySeq[Router => Route] = {

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
      documentationServer.routes ++
      metricsServer.routes
  }
}
