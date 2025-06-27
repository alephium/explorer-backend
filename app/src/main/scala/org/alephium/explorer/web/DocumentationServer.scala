// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.web

import scala.collection.immutable.ArraySeq

import io.vertx.ext.web._

import org.alephium.api.OpenAPIWriters.openApiJson
import org.alephium.explorer.GroupSetting
import org.alephium.explorer.docs.Documentation
import org.alephium.http.SwaggerUI
import org.alephium.util.Duration

class DocumentationServer(
    val maxTimeIntervalExportTxs: Duration,
    val currencies: ArraySeq[String]
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
