// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

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

  val chartServer = new ChartsServer(
    maxTimeInterval = ConfigDefaults.maxTimeIntervals.charts
  )
  override val routes = chartServer.routes

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
