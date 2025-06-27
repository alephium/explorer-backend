// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.tools

import org.alephium.api.OpenAPIWriters.openApiJson
import org.alephium.explorer.config._
import org.alephium.explorer.docs.Documentation
import org.alephium.protocol.model.NetworkId
import org.alephium.util.{discard, Duration}

object OpenApiUpdate {
  def main(args: Array[String]): Unit = {
    discard {
      new Documentation {

        private val config: ExplorerConfig = ExplorerConfig.loadNetwork(NetworkId.AlephiumMainNet)

        val groupNum                           = config.groupNum
        val maxTimeIntervalExportTxs: Duration = config.maxTimeInterval.exportTxs
        val currencies                         = config.market.currencies

        private val json = openApiJson(docs, dropAuth = false, truncateAddresses = true)

        import java.io.PrintWriter
        new PrintWriter("../app/src/main/resources/explorer-backend-openapi.json") {
          write(json)
          write('\n')
          close
        }
      }
    }
  }
}
