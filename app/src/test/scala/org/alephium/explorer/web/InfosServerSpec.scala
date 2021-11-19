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
import org.scalatest.concurrent.ScalaFutures

import org.alephium.explorer.{AlephiumSpec, BuildInfo}
import org.alephium.explorer.api.model.{ExplorerInfo, Pagination, TokenSupply}
import org.alephium.explorer.service.TokenSupplyService
import org.alephium.json.Json
import org.alephium.util.{Duration, TimeStamp, U256}

@SuppressWarnings(Array("org.wartremover.warts.Var"))
class InfosServerSpec()
    extends AlephiumSpec
    with AkkaDecodeFailureHandler
    with ScalaFutures
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

  it should "return the token supply list" in new Fixture {
    Get(s"/infos/supply") ~> server.route ~> check {
      responseAs[Seq[TokenSupply]] is Seq(tokenSupply)
    }
  }

  it should "return the token current supply" in new Fixture {
    Get(s"/infos/supply/current") ~> server.route ~> check {
      responseAs[TokenSupply] is tokenSupply
    }
  }

  it should "return the total token supply" in new Fixture {
    Get(s"/infos/supply/total") ~> server.route ~> check {
      val total = response.entity
        .toStrict(Duration.ofSecondsUnsafe(5).asScala)
        .map(_.data.utf8String)
        .futureValue

      total is "1"
    }
  }

  trait Fixture {
    val tokenSupply = TokenSupply(TimeStamp.zero, U256.One, U256.One, U256.One)
    val tokenSupplyService = new TokenSupplyService {
      def listTokenSupply(pagination: Pagination): Future[Seq[TokenSupply]] =
        Future.successful(
          Seq(
            tokenSupply
          ))
      def getLatestTokenSupply(): Future[Option[TokenSupply]] =
        Future.successful(
          Some(
            tokenSupply
          ))
      implicit def executionContext: ExecutionContext = ???
      def syncOnce(): Future[Unit]                    = ???
      def syncPeriod: Duration                        = ???

    }

    val server = new InfosServer(Duration.zero, tokenSupplyService)
  }
}
