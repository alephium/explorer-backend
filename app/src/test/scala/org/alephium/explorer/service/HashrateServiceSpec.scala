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

import scala.collection.immutable.ArraySeq
import scala.concurrent.ExecutionContext

import slick.jdbc.PostgresProfile.api._

import org.alephium.explorer.AlephiumFutureSpec
import org.alephium.explorer.Generators._
import org.alephium.explorer.api.model.{Hashrate, IntervalType}
import org.alephium.explorer.persistence.{DatabaseFixtureForEach, DBRunner}
import org.alephium.explorer.persistence.queries.HashrateQueries._
import org.alephium.explorer.persistence.schema.BlockHeaderSchema
import org.alephium.util._

class HashrateServiceSpec extends AlephiumFutureSpec with DatabaseFixtureForEach with DBRunner {
  implicit val executionContext: ExecutionContext = ExecutionContext.global

  "hourly hashrates" in new Fixture {

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

    run(BlockHeaderSchema.table ++= blocks).futureValue

    run(
      computeHourlyHashrate(from)
    ).futureValue.sortBy(_._1) is ArraySeq(
      v("2022-01-08T00:00:00.000Z", 3),
      v("2022-01-08T01:00:00.000Z", 30),
      v("2022-01-09T00:00:00.000Z", 100)
    )
  }

  "daily hashrates" in new Fixture {

    val blocks = Seq(
      b("2022-01-07T00:00:00.001Z", 2),
      b("2022-01-08T00:00:00.000Z", 4),
      //
      b("2022-01-08T00:00:00.001Z", 10),
      b("2022-01-08T23:59:00.001Z", 30),
      //
      b("2022-01-09T12:00:00.000Z", 100)
    )

    run(BlockHeaderSchema.table ++= blocks).futureValue

    run(
      computeDailyHashrate(from)
    ).futureValue.sortBy(_._1) is
      ArraySeq(
        v("2022-01-08T00:00:00.000Z", 3),
        v("2022-01-09T00:00:00.000Z", 20),
        v("2022-01-10T00:00:00.000Z", 100)
      )
    run(
      computeDailyHashrate(ts("2022-01-09T10:00:00.000Z"))
    ).futureValue.sortBy(_._1) is
      ArraySeq(
        v("2022-01-10T00:00:00.000Z", 100)
      )
  }

  "sync, update and return correct hashrates" in new Fixture {

    val blocks = Seq(
      b("2022-01-06T23:45:35.300Z", 1),
      b("2022-01-07T12:00:10.000Z", 2),
      b("2022-01-07T12:05:00.000Z", 4),
      b("2022-01-08T00:00:00.000Z", 12),
      b("2022-01-08T00:03:10.123Z", 100)
    )

    run(BlockHeaderSchema.table ++= blocks).futureValue

    HashrateService.get(from, to, IntervalType.Hourly).futureValue is ArraySeq.empty

    HashrateService.syncOnce().futureValue

    HashrateService.get(from, to, IntervalType.Hourly).futureValue is ArraySeq(
      hr("2022-01-07T00:00:00.000Z", 1),
      hr("2022-01-07T13:00:00.000Z", 3),
      hr("2022-01-08T00:00:00.000Z", 12),
      hr("2022-01-08T01:00:00.000Z", 100)
    )

    HashrateService.get(from, to, IntervalType.Daily).futureValue is ArraySeq(
      hr("2022-01-07T00:00:00.000Z", 1),
      hr("2022-01-08T00:00:00.000Z", 6),
      hr("2022-01-09T00:00:00.000Z", 100)
    )

    val newBlocks = Seq(
      b("2022-01-08T01:25:00.000Z", 10),
      b("2022-01-08T20:38:00.000Z", 4)
    )

    run(BlockHeaderSchema.table ++= newBlocks).futureValue

    HashrateService.syncOnce().futureValue

    HashrateService.get(from, to, IntervalType.Hourly).futureValue is ArraySeq(
      hr("2022-01-07T00:00:00.000Z", 1),
      hr("2022-01-07T13:00:00.000Z", 3),
      hr("2022-01-08T00:00:00.000Z", 12),
      hr("2022-01-08T01:00:00.000Z", 100),
      hr("2022-01-08T02:00:00.000Z", 10),
      hr("2022-01-08T21:00:00.000Z", 4)
    )

    HashrateService.get(from, to, IntervalType.Daily).futureValue is ArraySeq(
      hr("2022-01-07T00:00:00.000Z", 1),
      hr("2022-01-08T00:00:00.000Z", 6),
      hr("2022-01-09T00:00:00.000Z", 38)
    )
  }

  "correctly step back" in new Fixture {
    {
      val timestamp = ts("2022-01-08T12:21:34.321Z")

      HashrateService.computeHourlyStepBack(timestamp) is ts("2022-01-08T10:00:00.001Z")

      HashrateService.computeDailyStepBack(timestamp) is ts("2022-01-07T00:00:00.001Z")
    }
    {
      val timestamp = ts("2022-01-08T00:00:00.000Z")

      HashrateService.computeHourlyStepBack(timestamp) is ts("2022-01-07T22:00:00.001Z")

      HashrateService.computeDailyStepBack(timestamp) is ts("2022-01-07T00:00:00.001Z")
    }
    {
      val timestamp = ts("2022-01-08T23:59:59.999Z")

      HashrateService.computeHourlyStepBack(timestamp) is ts("2022-01-08T21:00:00.001Z")

      HashrateService.computeDailyStepBack(timestamp) is ts("2022-01-07T00:00:00.001Z")
    }
  }

  trait Fixture {

    val from = TimeStamp.zero
    val to   = TimeStamp.now()

    def ts(str: String): TimeStamp = {
      TimeStamp.unsafe(Instant.parse(str).toEpochMilli)
    }
    def bg(int: Double): BigDecimal = BigDecimal(int)

    def b(time: String, value: Double) = {
      blockHeaderWithHashrate(ts(time), value).sample.get
    }

    def v(time: String, value: Double)  = (ts(time), bg(value))
    def hr(time: String, value: Double) = Hashrate(ts(time), bg(value), bg(value))
  }
}
