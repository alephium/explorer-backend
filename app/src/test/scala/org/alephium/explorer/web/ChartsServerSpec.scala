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
import org.scalatest.concurrent.ScalaFutures

import org.alephium.api.ApiError
import org.alephium.explorer.AlephiumSpec
import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence.DatabaseFixtureForEach
import org.alephium.json.Json
import org.alephium.util.{Duration, TimeStamp}

@SuppressWarnings(Array("org.wartremover.warts.Var"))
class ChartsServerSpec()
    extends AlephiumSpec
    with AkkaDecodeFailureHandler
    with DatabaseFixtureForEach
    with ScalaFutures
    with ScalatestRouteTest
    with UpickleCustomizationSupport {
  override type Api = Json.type

  override def api: Api = Json

  "validate hourly/daily time range " in new Fixture {
    val now     = TimeStamp.now().millis
    val days30  = Duration.ofDaysUnsafe(30)
    val millis1 = Duration.ofMillisUnsafe(1).millis

    val fromTs = now - days30.millis

    def test(endpoint: String) = {
      Get(s"/charts/$endpoint?fromTs=${fromTs}&toTs=${now}&interval-type=hourly") ~> server.route ~> check {
        responseAs[Seq[Hashrate]] is Seq.empty
      }
      Get(s"/charts/$endpoint?fromTs=${fromTs - millis1}&toTs=${now}&interval-type=hourly") ~> server.route ~> check {
        responseAs[ApiError.BadRequest] is ApiError.BadRequest(
          s"Time span cannot be greater than $days30"
        )
      }

      Get(s"/charts/$endpoint?fromTs=${fromTs}&toTs=${now}&interval-type=daily") ~> server.route ~> check {
        responseAs[Seq[Hashrate]] is Seq.empty
      }
      Get(s"/charts/$endpoint?fromTs=${fromTs - millis1}&toTs=${now}&interval-type=daily") ~> server.route ~> check {
        responseAs[Seq[Hashrate]] is Seq.empty
      }
    }

    test("hashrates")
    test("transactions-count")
    test("transactions-count-per-chain")
  }

  trait Fixture {
    val server =
      new ChartsServer()
  }
}
