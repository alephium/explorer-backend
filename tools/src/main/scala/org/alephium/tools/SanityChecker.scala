// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.tools

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._
import scala.util.control.NonFatal

import com.typesafe.scalalogging.StrictLogging
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import org.alephium.explorer.GroupSetting
import org.alephium.explorer.cache.BlockCache
import org.alephium.explorer.config._
import org.alephium.explorer.service.BlockFlowClient
import org.alephium.explorer.util.ExecutionContextUtil
import org.alephium.util.discard

object SanityChecker {
  def main(args: Array[String]): Unit = {
    discard(new Runner().run())
  }

  private final class Runner extends StrictLogging {
    private val executor = java.util.concurrent.Executors.newCachedThreadPool()

    private val typesafeConfig = ExplorerConfig.loadConfig(Platform.getRootPath()) match {
      case scala.util.Success(config) => config
      case scala.util.Failure(error)  => throw error
    }

    implicit private val config: ExplorerConfig = ExplorerConfig.load(typesafeConfig)

    implicit private val databaseConfig: DatabaseConfig[PostgresProfile] =
      DatabaseConfig.forConfig[PostgresProfile]("db", typesafeConfig)

    implicit private val executionContext: ExecutionContext =
      ExecutionContextUtil.fromExecutor(executor, ())

    implicit private val groupSetting: GroupSetting = GroupSetting(config.groupNum)

    implicit private val blockCache: BlockCache =
      BlockCache(
        config.cacheRowCountReloadPeriod,
        config.cacheBlockTimesReloadPeriod,
        config.cacheLatestBlocksReloadPeriod
      )

    implicit private val blockFlowClient: BlockFlowClient =
      BlockFlowClient(
        uri = config.blockFlowUri,
        groupNum = config.groupNum,
        maybeApiKey = config.maybeBlockFlowApiKey,
        directCliqueAccess = config.directCliqueAccess,
        consensus = config.consensus
      )

    def run(): Unit = {
      try {
        Await.result(
          org.alephium.explorer.service.SanityChecker.checkAndStopOnProblem(),
          Duration.Inf
        )
        logger.info("Sanity checker finished without finding a problematic block")
      } catch {
        case NonFatal(error) =>
          logger.error("Sanity checker stopped after finding a problematic block", error)
          sys.exit(1)
      } finally {
        databaseConfig.db.close()
        executor.shutdown()
      }
    }
  }
}
