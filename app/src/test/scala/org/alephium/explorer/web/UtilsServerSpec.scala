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

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import de.heikoseeberger.akkahttpupickle.UpickleCustomizationSupport
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.ScalaFutures

import org.alephium.api.ApiError
import org.alephium.explorer.{AlephiumSpec, Generators, GroupSetting}
import org.alephium.explorer.api.model.LogbackValue
import org.alephium.explorer.cache.{BlockCache, TransactionCache}
import org.alephium.explorer.persistence.{Database, DatabaseFixtureForEach}
import org.alephium.explorer.service._
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

  "update global loglevel" in new Fixture {
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
          s"Invalid value for: body (expected value to be one of (TRACE, DEBUG, INFO, WARN, ERROR), but was $level)")
      }
    }
  }

  "update logback values" in new Fixture {

    val logbackValues: Seq[LogbackValue] = Seq(
      LogbackValue("org.test", LogbackValue.Level.Debug),
      LogbackValue("yop", LogbackValue.Level.Trace)
    )

    Put(s"/utils/update-log-config", logbackValues) ~> server.route ~> check {
      status is StatusCodes.OK
    }

    val json   = """
    [
      {
        "name": "foo",
        "level": "boo"
      }
    ]
    """
    val entity = HttpEntity(ContentTypes.`application/json`, json)

    Put(s"/utils/update-log-config", entity) ~> server.route ~> check {
      responseAs[ApiError.BadRequest] is ApiError.BadRequest(
        s"Invalid value for: body (Cannot decode level, expected one of: ArraySeq(TRACE, DEBUG, INFO, WARN, ERROR) at index 55: decoding failure)")
    }
  }

  trait Fixture {
    implicit val blockFlowClient: BlockFlowClient   = mock[BlockFlowClient]
    implicit val groupSettings: GroupSetting        = Generators.groupSettingGen.sample.get
    implicit val blockCache: BlockCache             = BlockCache()
    implicit val transactionCache: TransactionCache = TransactionCache(new Database(false))

    val server =
      new UtilsServer()
  }
}
