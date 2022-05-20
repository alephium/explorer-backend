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
import org.scalatest.wordspec.AnyWordSpec

import org.alephium.explorer.AlephiumSpec._
import org.alephium.explorer.Generators
import org.alephium.explorer.api.model.IntervalType
import org.alephium.explorer.persistence.{DatabaseFixtureForEach, DBRunner}
import org.alephium.util._

class TransactionHistoryServiceSpec
    extends AnyWordSpec
    with DatabaseFixtureForEach
    with DBRunner
    with Generators
    with ScalaFutures
    with Eventually {
  implicit val executionContext: ExecutionContext = ExecutionContext.global
  override implicit val patienceConfig            = PatienceConfig(timeout = Span(1, Minutes))

  def ts(str: String): TimeStamp = {
    TimeStamp.unsafe(Instant.parse(str).toEpochMilli)
  }

  "getTimeRanges" should {
    "build daily time ranges" in {
      TransactionHistoryService.getTimeRanges(
        ts("2022-01-08T09:54:32.101Z"),
        ts("2022-01-10T12:34:56.789Z"),
        IntervalType.Daily
      ) is
        Seq(
          (ts("2022-01-08T00:00:00.000Z"), ts("2022-01-08T23:59:59.999Z")),
          (ts("2022-01-09T00:00:00.000Z"), ts("2022-01-09T23:59:59.999Z"))
          //2022-01-10 isn't done, so we don't count it
        )
    }

    "build hourly time ranges" in {
      TransactionHistoryService.getTimeRanges(
        ts("2022-01-08T09:54:32.101Z"),
        ts("2022-01-08T12:34:56.789Z"),
        IntervalType.Hourly
      ) is
        Seq(
          (ts("2022-01-08T09:00:00.000Z"), ts("2022-01-08T09:59:59.999Z")),
          (ts("2022-01-08T10:00:00.000Z"), ts("2022-01-08T10:59:59.999Z")),
          (ts("2022-01-08T11:00:00.000Z"), ts("2022-01-08T11:59:59.999Z"))
          //12:34:56 isn't done, so we don't count it
        )
    }

    "return empty range when start is after end" in {

      TransactionHistoryService.getTimeRanges(
        ts("2022-01-08T00:00:00.000Z"),
        ts("2022-01-07T00:00:00.000Z"),
        IntervalType.Hourly
      ) is Seq.empty
    }

    "return empty range when end == start" in {

      TransactionHistoryService.getTimeRanges(
        ts("2022-01-08T00:00:00.000Z"),
        ts("2022-01-08T00:00:00.000Z"),
        IntervalType.Hourly
      ) is Seq.empty
    }
  }
}
