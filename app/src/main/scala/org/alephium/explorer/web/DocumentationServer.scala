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

package org.alephium.explorer.web

import scala.collection.immutable.{ArraySeq, ListMap}

import io.vertx.ext.web._

import org.alephium.api.OpenAPIWriters.openApiJson
import org.alephium.explorer.GroupSetting
import org.alephium.explorer.docs.Documentation
import org.alephium.http.SwaggerUI
import org.alephium.util.Duration

class DocumentationServer(
    val maxTimeIntervalExportTxs: Duration,
    val currencies: ArraySeq[String],
    val tokensWithPrice: ListMap[String, String]
)(implicit
    groupSetting: GroupSetting
) extends Server
    with Documentation {

  val groupNum = groupSetting.groupNum

  val routes: ArraySeq[Router => Route] =
    ArraySeq.from(
      SwaggerUI(
        openApiJson(docs, dropAuth = false, truncateAddresses = true),
        openapiFileName = "explorer-backend-openapi.json"
      ).map(route(_))
    )
}
