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

package org.alephium.explorer.service

import java.time.Instant

import scala.concurrent.ExecutionContext

import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Minutes, Span}

import org.alephium.explorer.api.model.Hashrate
import org.alephium.explorer.{AlephiumSpec, Generators}
import org.alephium.explorer.persistence.{DatabaseFixture, DBRunner}
import org.alephium.explorer.persistence.queries.HashrateQueries
import org.alephium.explorer.persistence.schema.BlockHeaderSchema
import org.alephium.util._

class HashrateServiceSpec extends AlephiumSpec with ScalaFutures with Eventually {
  implicit val executionContext: ExecutionContext = ExecutionContext.global
  override implicit val patienceConfig            = PatienceConfig(timeout = Span(1, Minutes))
  it should "10 minutes hashrates" in new Fixture {
    import config.profile.api._

    /*
     * Format:
     *
     * (TimeStamp, Hashrate)
     *
     * Empty comments are here to show each group of time interval
     */
    val blocks = Seq(
      b("2022-01-07T23:50:00.000Z", 1),
      //
      b("2022-01-07T23:50:00.001Z", 2),
      b("2022-01-08T00:00:00.000Z", 4),
      //
      b("2022-01-08T00:00:00.001Z", 10),
      b("2022-01-08T00:08:23.123Z", 30),
      b("2022-01-08T00:10:00.000Z", 50),
      //
      b("2022-01-08T00:11:00.000Z", 100)
    )

    run(blockHeadersTable ++= blocks).futureValue

    run(
      compute10MinutesHashrate(from)
    ).futureValue.sortBy(_._1) is
      Vector(
        v("2022-01-07T23:50:00.000Z", 1),
        v("2022-01-08T00:00:00.000Z", 3),
        v("2022-01-08T00:10:00.000Z", 30),
        v("2022-01-08T00:20:00.000Z", 100)
      )

    run(
      compute10MinutesHashrate(ts("2022-01-08T00:10:00.000Z"))
    ).futureValue.sortBy(_._1) is
      Vector(
        v("2022-01-08T00:10:00.000Z", 50),
        v("2022-01-08T00:20:00.000Z", 100)
      )
  }

  it should "hourly hashrates" in new Fixture {
    import config.profile.api._

    val blocks = Seq(
      b("2022-01-07T23:00:00.001Z", 2),
      b("2022-01-08T00:00:00.000Z", 4),
      //
      b("2022-01-08T00:00:00.001Z", 10),
      b("2022-01-08T00:18:23.123Z", 30),
      b("2022-01-08T01:00:00.000Z", 50),
      //
      b("2022-01-09T00:00:00.000Z", 100)
    )

    run(blockHeadersTable ++= blocks).futureValue

    run(
      computeHourlyHashrate(from)
    ).futureValue.sortBy(_._1) is Vector(
      v("2022-01-08T00:00:00.000Z", 3),
      v("2022-01-08T01:00:00.000Z", 30),
      v("2022-01-09T00:00:00.000Z", 100)
    )
  }

  it should "daily hashrates" in new Fixture {
    import config.profile.api._

    val blocks = Seq(
      b("2022-01-07T00:00:00.001Z", 2),
      b("2022-01-08T00:00:00.000Z", 4),
      //
      b("2022-01-08T00:00:00.001Z", 10),
      //
      b("2022-01-09T12:00:00.000Z", 100)
    )

    run(blockHeadersTable ++= blocks).futureValue

    run(
      computeDailyHashrate(from)
    ).futureValue.sortBy(_._1) is
      Vector(
        v("2022-01-08T00:00:00.000Z", 3),
        v("2022-01-09T00:00:00.000Z", 10),
        v("2022-01-10T00:00:00.000Z", 100)
      )
    run(
      computeDailyHashrate(ts("2022-01-09T10:00:00.000Z"))
    ).futureValue.sortBy(_._1) is
      Vector(
        v("2022-01-10T00:00:00.000Z", 100)
      )
  }

  it should "sync and return correct hashrates" in new Fixture {
    import config.profile.api._

    val blocks = Seq(
      b("2022-01-07T12:00:10.000Z", 2),
      b("2022-01-07T12:05:00.000Z", 4),
      b("2022-01-08T00:00:00.000Z", 12)
    )

    run(blockHeadersTable ++= blocks).futureValue

    hashrateService.get(from, to, 0).futureValue is Vector.empty

    hashrateService.syncOnce().futureValue

    hashrateService.get(from, to, 0).futureValue is
      Vector(
        hr("2022-01-07T12:10:00.000Z", 3),
        hr("2022-01-08T00:00:00.000Z", 12)
      )

    hashrateService.get(from, to, 1).futureValue is Vector(
      hr("2022-01-07T13:00:00.000Z", 3),
      hr("2022-01-08T00:00:00.000Z", 12)
    )

    hashrateService.get(from, to, 2).futureValue is Vector(
      hr("2022-01-08T00:00:00.000Z", 6)
    )
  }

  trait Fixture
      extends HashrateQueries
      with DatabaseFixture
      with DBRunner
      with BlockHeaderSchema
      with Generators {

    val from = TimeStamp.zero
    val to   = TimeStamp.now()

    val hashrateService: HashrateService =
      HashrateService(syncPeriod = Duration.unsafe(1000), databaseConfig)

    override val config = databaseConfig

    def ts(str: String): TimeStamp = {
      TimeStamp.unsafe(Instant.parse(str).toEpochMilli)
    }
    def bg(int: Double): BigDecimal = BigDecimal(int)

    def b(time: String, value: Double) = {
      blockHeaderWithHashrate(ts(time), value).sample.get
    }

    def v(time: String, value: Double)  = (ts(time), bg(value))
    def hr(time: String, value: Double) = Hashrate(ts(time), bg(value))
  }
}
