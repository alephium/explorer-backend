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

import org.alephium.api.ApiError
import org.alephium.explorer._
import org.alephium.explorer.HttpFixture._
import org.alephium.explorer.api.model._
import org.alephium.explorer.persistence.DatabaseFixtureForAll
import org.alephium.util.{Duration, TimeStamp}

@SuppressWarnings(Array("org.wartremover.warts.Var"))
class ChartsServerSpec()
    extends AlephiumActorSpecLike
    with DatabaseFixtureForAll
    with HttpServerFixture {

  val server: Server = new ChartsServer(
    maxTimeInterval = ConfigDefaults.maxTimeIntervals.charts
  )

  "validate hourly/daily time range " in {
    val now     = TimeStamp.now().millis
    val days30  = Duration.ofDaysUnsafe(30)
    val millis1 = Duration.ofMillisUnsafe(1).millis

    val fromTs = now - days30.millis

    def test(endpoint: String) = {
      Get(s"/charts/$endpoint?fromTs=${fromTs}&toTs=${now}&interval-type=hourly") check {
        response =>
          response.as[Seq[Hashrate]] is Seq.empty
      }
      Get(s"/charts/$endpoint?fromTs=${fromTs - millis1}&toTs=${now}&interval-type=hourly") check {
        response =>
          response.as[ApiError.BadRequest] is ApiError.BadRequest(
            s"Time span cannot be greater than $days30"
          )
      }

      Get(s"/charts/$endpoint?fromTs=${fromTs}&toTs=${now}&interval-type=daily") check { response =>
        response.as[Seq[Hashrate]] is Seq.empty
      }
      Get(s"/charts/$endpoint?fromTs=${fromTs - millis1}&toTs=${now}&interval-type=daily") check {
        response =>
          response.as[Seq[Hashrate]] is Seq.empty
      }
    }

    test("hashrates")
    test("transactions-count")
    test("transactions-count-per-chain")
  }
}
