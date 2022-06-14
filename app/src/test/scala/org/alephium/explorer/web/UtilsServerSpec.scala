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

import scala.io.Source

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import de.heikoseeberger.akkahttpupickle.UpickleCustomizationSupport
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.ScalaFutures

import org.alephium.api.ApiError
import org.alephium.explorer.{AlephiumSpec, Generators, GroupSetting, Main}
import org.alephium.explorer.cache.{BlockCache, TransactionCache}
import org.alephium.explorer.persistence.DatabaseFixtureForEach
import org.alephium.explorer.service._
import org.alephium.explorer.util.TestUtils._
import org.alephium.json.Json

@SuppressWarnings(Array("org.wartremover.warts.Var"))
class UtilsServerSpec()
    extends AlephiumSpec
    with AkkaDecodeFailureHandler
    with DatabaseFixtureForEach
    with ScalaFutures
    with ScalatestRouteTest
    with UpickleCustomizationSupport
    with MockFactory {
  override type Api = Json.type

  override def api: Api = Json

  it should "update global loglevel" in new Fixture {
    List("TRACE", "DEBUG", "INFO", "WARN", "ERROR").foreach { level =>
      val entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, level)
      Put(s"/utils/update-global-loglevel", entity) ~> server.route ~> check {
        status is StatusCodes.OK
      }
    }
    List("Trace", "debug", "yop").foreach { level =>
      val entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, level)
      Put(s"/utils/update-global-loglevel", entity) ~> server.route ~> check {
        responseAs[ApiError.BadRequest] is ApiError.BadRequest(
          s"Invalid value for: body (expected value to be within List(TRACE, DEBUG, INFO, WARN, ERROR), but was '$level')")
      }
    }
  }

  it should "update logback file" in new Fixture {
    val logbackFile =
      using(
        Source.fromFile(name = Main.getClass.getResource("/logback.xml").getPath, enc = "UTF-8")
      )(_.getLines().toList).mkString("\n")

    val entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, logbackFile)
    Put(s"/utils/update-log-config", entity) ~> server.route ~> check {
      status is StatusCodes.OK
    }

    val wrongConfig = """
        <?xml version="1.0" encoding="UTF-8"?>
        <configuration>
        <configuration>
    """
    val wrongEntity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, wrongConfig)

    Put(s"/utils/update-log-config", wrongEntity) ~> server.route ~> check {
      responseAs[ApiError.BadRequest] is ApiError.BadRequest(
        s"Cannot apply logback configuation: Problem parsing XML document. See previously reported errors.")
    }
  }

  trait Fixture extends Generators {
    implicit val blockFlowClient: BlockFlowClient   = mock[BlockFlowClient]
    implicit val groupSettings: GroupSetting        = GroupSetting(groupNum)
    implicit val blockCache: BlockCache             = BlockCache()
    implicit val transactionCache: TransactionCache = TransactionCache()

    val server =
      new UtilsServer()
  }
}
