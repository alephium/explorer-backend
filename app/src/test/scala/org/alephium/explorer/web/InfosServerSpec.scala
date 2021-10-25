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

import akka.http.scaladsl.testkit.ScalatestRouteTest
import de.heikoseeberger.akkahttpupickle.UpickleCustomizationSupport

import org.alephium.explorer.{AlephiumSpec, BuildInfo}
import org.alephium.explorer.api.model.ExplorerInfo
import org.alephium.json.Json
import org.alephium.util.Duration

@SuppressWarnings(Array("org.wartremover.warts.Var"))
class InfosServerSpec()
    extends AlephiumSpec
    with AkkaDecodeFailureHandler
    with ScalatestRouteTest
    with UpickleCustomizationSupport {
  override type Api = Json.type

  override def api: Api = Json

  it should "return the explorer infos" in {
    val server = new InfosServer(Duration.zero)

    Get(s"/infos") ~> server.route ~> check {
      responseAs[ExplorerInfo] is ExplorerInfo(
        BuildInfo.releaseVersion,
        BuildInfo.commitId
      )
    }
  }
}
