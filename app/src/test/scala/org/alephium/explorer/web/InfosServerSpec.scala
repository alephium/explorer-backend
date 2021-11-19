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

import scala.concurrent.{ExecutionContext, Future}

import akka.http.scaladsl.testkit.ScalatestRouteTest
import de.heikoseeberger.akkahttpupickle.UpickleCustomizationSupport

import org.alephium.explorer.{AlephiumSpec, BuildInfo}
import org.alephium.explorer.api.model.{ExplorerInfo, Pagination, TokenSupply}
import org.alephium.explorer.service.TokenSupplyService
import org.alephium.json.Json
import org.alephium.util.{Duration, TimeStamp, U256}

@SuppressWarnings(Array("org.wartremover.warts.Var"))
class InfosServerSpec()
    extends AlephiumSpec
    with AkkaDecodeFailureHandler
    with ScalatestRouteTest
    with UpickleCustomizationSupport {
  override type Api = Json.type

  override def api: Api = Json

  it should "return the explorer infos" in new Fixture {
    Get(s"/infos") ~> server.route ~> check {
      responseAs[ExplorerInfo] is ExplorerInfo(
        BuildInfo.releaseVersion,
        BuildInfo.commitId
      )
    }
  }

  it should "return the token supply infos" in new Fixture {
    Get(s"/infos/token-supply") ~> server.route ~> check {
      responseAs[Seq[TokenSupply]] is Seq(TokenSupply(TimeStamp.zero, U256.One))
    }
  }

  trait Fixture {
    val tokenSupplyService = new TokenSupplyService {
      def listTokenSupply(pagination: Pagination): Future[Seq[TokenSupply]] =
        Future.successful(
          Seq(
            TokenSupply(TimeStamp.zero, U256.One)
          ))
      def getLatestTokenSupply(): Future[Option[TokenSupply]] =
        Future.successful(
          Some(
            TokenSupply(TimeStamp.zero, U256.One)
          ))
      implicit def executionContext: ExecutionContext = ???
      def syncOnce(): Future[Unit]                    = ???
      def syncPeriod: Duration                        = ???

    }

    val server = new InfosServer(Duration.zero, tokenSupplyService)
  }
}
