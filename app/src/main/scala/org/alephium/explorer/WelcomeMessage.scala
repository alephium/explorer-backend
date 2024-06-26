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
