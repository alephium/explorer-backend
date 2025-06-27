// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._
import scala.util._

import com.typesafe.scalalogging.StrictLogging
import io.prometheus.client.hotspot.DefaultExports
import slick.basic.DatabaseConfig
import slick.jdbc.PostgresProfile

import org.alephium.explorer.config._
import org.alephium.explorer.util.ExecutionContextUtil
import org.alephium.util.discard

object Main extends StrictLogging {
  def main(args: Array[String]): Unit = {
    try {
      (new BootUp).init()
    } catch {
      case error: Throwable =>
        logger.error("Cannot initialize system", error)
    }
  }
}

@SuppressWarnings(Array("org.wartremover.warts.TryPartial"))
class BootUp extends StrictLogging {
  logger.info("Starting Explorer")
  logger.info(s"Build info: $BuildInfo")

  DefaultExports.initialize()

  private val typesafeConfig = ExplorerConfig.loadConfig(Platform.getRootPath()) match {
    case Success(config) => config
    case Failure(error)  => throw error
  }

  implicit val config: ExplorerConfig = ExplorerConfig.load(typesafeConfig)

  implicit val databaseConfig: DatabaseConfig[PostgresProfile] =
    DatabaseConfig.forConfig[PostgresProfile]("db", typesafeConfig)

  def init(): Unit = {
    // scalastyle:off null
    var explorer: ExplorerState = null
    // scalastyle:on null

    @SuppressWarnings(Array("org.wartremover.warts.GlobalExecutionContext"))
    implicit val executionContext: ExecutionContext =
      ExecutionContextUtil.fromExecutor(ExecutionContext.global, discard(explorer.stop()))

    explorer = ExplorerState(config.bootMode)

    explorer
      .start()
      .onComplete {
        case Success(_) => logger.info(WelcomeMessage.message(config, typesafeConfig))
        case Failure(error) =>
          logger.error("Fatal error during initialization", error)
          explorer.stop().failed foreach { error =>
            logger.error("Failed to stop explorer", error)
          }
      }

    Runtime.getRuntime.addShutdownHook(new Thread(() => {
      discard(Await.result(explorer.stop(), 10.seconds))
    }))
  }
}
