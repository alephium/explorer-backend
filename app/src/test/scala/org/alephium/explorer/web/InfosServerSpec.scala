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
import org.alephium.explorer.api.model._
import org.alephium.explorer.service.{BlockService, TokenSupplyService}
import org.alephium.json.Json
import org.alephium.protocol.ALPH
import org.alephium.util.{Duration, TimeStamp}

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

  it should "return chains heights" in new Fixture {
    Get(s"/infos/heights") ~> server.route ~> check {
      responseAs[Seq[PerChainValue]] is Seq(chainHeight)
    }
  }

  it should "return the token supply list" in new Fixture {
    Get(s"/infos/supply") ~> server.route ~> check {
      responseAs[Seq[TokenSupply]] is Seq(tokenSupply)
    }
  }

  it should "return the token current supply" in new Fixture {
    Get(s"/infos/supply/circulating-alph") ~> server.route ~> check {
      val circulating = response.entity
        .toStrict(Duration.ofSecondsUnsafe(5).asScala)
        .map(_.data.utf8String)
        .futureValue

      circulating is "2"
    }
  }

  it should "return the total token supply" in new Fixture {
    Get(s"/infos/supply/total-alph") ~> server.route ~> check {
      val total = response.entity
        .toStrict(Duration.ofSecondsUnsafe(5).asScala)
        .map(_.data.utf8String)
        .futureValue

      total is "1"
    }
  }

  trait Fixture {
    val tokenSupply = TokenSupply(TimeStamp.zero, ALPH.alph(1), ALPH.alph(2), ALPH.alph(3))
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

    val chainHeight = PerChainValue(0, 0, 1)
    val blockService = new BlockService {
      def getLiteBlockByHash(hash: BlockEntry.Hash): Future[Option[BlockEntryLite]] = ???
      def getBlockTransactions(hash: BlockEntry.Hash,
                               pagination: Pagination): Future[Seq[Transaction]] = ???
      def listBlocks(pagination: Pagination): Future[ListBlocks]                 = ???
      def listMaxHeights(): Future[Seq[PerChainValue]]                           = Future.successful(Seq(chainHeight))
    }

    val server = new InfosServer(Duration.zero, tokenSupplyService, blockService)
  }
}
