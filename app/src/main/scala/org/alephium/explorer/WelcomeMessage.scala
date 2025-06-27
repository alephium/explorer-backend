// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer

import com.typesafe.config.Config

import org.alephium.explorer.config.{BootMode, ExplorerConfig}

object WelcomeMessage {

  def message(config: ExplorerConfig, typesafeConfig: Config): String = {

    val logCurl =
      s"The API: curl -X 'PUT' '${config.host}:${config.port}/utils/update-global-loglevel' -d 'DEBUG'"
    val logEnv =
      s"An environment variable: `EXPLORER_LOG_LEVEL=DEBUG`"

    val readInfo =
      if (BootMode.readable(config.bootMode)) {
        s"Access the API at: ${config.host}:${config.port}/docs"
      } else {
        "No API available in this mode"
      }

    val loggingInfo = {
      "Activate debug logs with:\n" ++
        (if (BootMode.readable(config.bootMode)) {
           s"""|* $logCurl
               |* $logEnv
               |""".stripMargin
         } else {
           s"* $logEnv"
         })
    }

    val writeInfo =
      if (BootMode.writable(config.bootMode)) {
        s"Syncing blocks with node at: ${config.blockFlowUri}"
      } else {
        "No syncing in this mode"
      }

    val networkInfo = {
      s"Network id: ${config.networkId.id}"
    }

    val dbInfo = {
      s"Database: ${typesafeConfig.getString("db.db.url")}"
    }

    s"""|
        |############################################
        |Explorer-backend started with ${config.bootMode} mode
        |${networkInfo}
        |${dbInfo}
        |${writeInfo}
        |${readInfo}
        |${loggingInfo}
        |############################################
        |""".stripMargin
  }
}
