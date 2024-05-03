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

package org.alephium.tools

import com.typesafe.config.ConfigFactory

import org.alephium.api.OpenAPIWriters.openApiJson
import org.alephium.explorer.config.ExplorerConfig
import org.alephium.explorer.docs.Documentation
import org.alephium.util.{discard, Duration}

object OpenApiUpdate {
  def main(args: Array[String]): Unit = {
    discard {
      new Documentation {

        private val typesafeConfig = ConfigFactory.load()

        val config: ExplorerConfig = ExplorerConfig.load(typesafeConfig)
        val servicesConfig         = config.services

        // scalastyle:off magic.number
        val groupNum                           = 4
        val maxTimeIntervalExportTxs: Duration = Duration.ofDaysUnsafe(366)
        // scalastyle:on magic.number

        private val json = openApiJson(docs, dropAuth = false)

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
