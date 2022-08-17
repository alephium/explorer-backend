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

import scala.concurrent.duration._

import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import de.heikoseeberger.akkahttpupickle.UpickleCustomizationSupport
import org.scalacheck.Gen
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Minutes, Span}

import org.alephium.explorer.AlephiumSpec
import org.alephium.explorer.GenApiModel._
import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence.DatabaseFixtureForEach
import org.alephium.explorer.persistence.dao.UnconfirmedTxDao
import org.alephium.json.Json

@SuppressWarnings(Array("org.wartremover.warts.ThreadSleep", "org.wartremover.warts.Var"))
class UnconfirmedTransactionServerSpec()
    extends AlephiumSpec
    with AkkaDecodeFailureHandler
    with DatabaseFixtureForEach
    with ScalatestRouteTest
    with ScalaFutures
    with Eventually
    with UpickleCustomizationSupport {
  override type Api = Json.type

  override def api: Api = Json

  override implicit val patienceConfig = PatienceConfig(timeout = Span(1, Minutes))
  implicit val defaultTimeout          = RouteTestTimeout(5.seconds)

  "listUnconfirmedTransactions" in new Fixture {
    Get(s"/unconfirmed-transactions") ~> server.route ~> check {
      responseAs[Seq[UnconfirmedTransaction]] is Seq.empty
    }

    forAll(Gen.listOf(utransactionGen), Gen.choose(1, 2), Gen.choose(2, 4)) {
      case (utxs, page, limit) =>
        UnconfirmedTxDao.insertMany(utxs).futureValue
        Get(s"/unconfirmed-transactions?page=$page&limit=$limit") ~> server.route ~> check {
          val offset = page - 1
          val drop   = offset * limit
          responseAs[Seq[UnconfirmedTransaction]] is utxs
            .sortBy(_.lastSeen)
            .reverse
            .slice(drop, drop + limit)
        }
        UnconfirmedTxDao.removeMany(utxs.map(_.hash)).futureValue
    }
  }

  trait Fixture {
    lazy val server = new UnconfirmedTransactionServer()
  }
}
