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

import akka.http.scaladsl.server.Route
import sttp.tapir.server.akkahttp.AkkaHttpServerOptions
import sttp.tapir.swagger.akkahttp.SwaggerAkka

import org.alephium.api.OpenAPIWriters.openApiJson
import org.alephium.explorer.docs.Documentation
import org.alephium.protocol.model.NetworkType
import org.alephium.util.Duration

class DocumentationServer(val networkType: NetworkType, val blockflowFetchMaxAge: Duration)(
    implicit val serverOptions: AkkaHttpServerOptions)
    extends Server
    with Documentation {

  val route: Route =
    new SwaggerAkka(openApiJson(docs), yamlName = "explorer-backend-openapi.json").routes
}
