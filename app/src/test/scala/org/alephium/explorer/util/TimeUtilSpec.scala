// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.util

import java.time.{Instant, LocalDateTime, OffsetTime, ZoneId}

import scala.collection.immutable.ArraySeq

import org.scalacheck.Gen
import org.scalatest.TryValues._
import org.scalatest.matchers.should.Matchers

import org.alephium.explorer.AlephiumSpec
import org.alephium.explorer.GenCoreUtil._
import org.alephium.explorer.api.model.IntervalType
import org.alephium.explorer.error.ExplorerError.RemoteTimeStampIsBeforeLocal
import org.alephium.explorer.util.TimeUtil._
import org.alephium.util.{Duration, TimeStamp}

class TimeUtilSpec extends AlephiumSpec with Matchers {

  "getTimeRanges" should {
    "build daily time ranges" in {
      getTimeRanges(
        ts("2022-01-08T09:54:32.101Z"),
        ts("2022-01-10T12:34:56.789Z"),
        IntervalType.Daily
      ) is
        ArraySeq(
          (ts("2022-01-08T00:00:00.000Z"), ts("2022-01-08T23:59:59.999Z")),
          (ts("2022-01-09T00:00:00.000Z"), ts("2022-01-09T23:59:59.999Z"))
          // 2022-01-10 isn't done, so we don't count it
        )
    }

    "build hourly time ranges" in {
      getTimeRanges(
        ts("2022-01-08T09:54:32.101Z"),
        ts("2022-01-08T12:34:56.789Z"),
        IntervalType.Hourly
      ) is
        ArraySeq(
          (ts("2022-01-08T09:00:00.000Z"), ts("2022-01-08T09:59:59.999Z")),
          (ts("2022-01-08T10:00:00.000Z"), ts("2022-01-08T10:59:59.999Z")),
          (ts("2022-01-08T11:00:00.000Z"), ts("2022-01-08T11:59:59.999Z"))
          // 12:34:56 isn't done, so we don't count it
        )
    }

    "build weekly time ranges" in {
      getTimeRanges(
        ts("2025-12-31T09:54:32.101Z"),
        ts("2026-01-13T09:54:32.101Z"),
        IntervalType.Weekly
      ) is
        ArraySeq(
          (ts("2025-12-29T00:00:00.000Z"), ts("2026-01-04T23:59:59.999Z")),
          (ts("2026-01-05T00:00:00.000Z"), ts("2026-01-11T23:59:59.999Z"))
        )
    }

    "build monthly time ranges" in {
      getTimeRanges(
        ts("2022-01-08T09:54:32.101Z"),
        ts("2022-05-10T12:34:56.789Z"),
        IntervalType.Monthly
      ) is
        ArraySeq(
          (ts("2022-01-01T00:00:00.000Z"), ts("2022-01-31T23:59:59.999Z")),
          (ts("2022-02-01T00:00:00.000Z"), ts("2022-02-28T23:59:59.999Z")),
          (ts("2022-03-01T00:00:00.000Z"), ts("2022-03-31T23:59:59.999Z")),
          (ts("2022-04-01T00:00:00.000Z"), ts("2022-04-30T23:59:59.999Z"))
        )
    }

    "build monthly time ranges over a year" in {
      getTimeRanges(
        ts("2022-12-08T09:54:32.101Z"),
        ts("2023-02-10T12:34:56.789Z"),
        IntervalType.Monthly
      ) is
        ArraySeq(
          (ts("2022-12-01T00:00:00.000Z"), ts("2022-12-31T23:59:59.999Z")),
          (ts("2023-01-01T00:00:00.000Z"), ts("2023-01-31T23:59:59.999Z"))
        )
    }

    "return empty range when start is after end" in {

      getTimeRanges(
        ts("2022-01-08T00:00:00.000Z"),
        ts("2022-01-07T00:00:00.000Z"),
        IntervalType.Hourly
      ) is ArraySeq.empty
    }

    "return empty range when end == start" in {

      getTimeRanges(
        ts("2022-01-08T00:00:00.000Z"),
        ts("2022-01-08T00:00:00.000Z"),
        IntervalType.Hourly
      ) is ArraySeq.empty
    }
  }

  "toZonedDateTime" should {
    "convert OffsetTime to ZonedDateTime with today's date" in {
      val zone         = ZoneId.of("Australia/Perth")
      val expectedDate = LocalDateTime.now(zone) // expected day/month/year
      val expectedTime = OffsetTime.now(zone)    // expected hour/minute/day

      val actual = toZonedDateTime(expectedTime) // actual ZonedDateTime

      // check time
      actual.getHour is expectedTime.getHour
      actual.getMinute is expectedTime.getMinute
      actual.getSecond is expectedTime.getSecond
      // check year
      actual.getDayOfYear is expectedDate.getDayOfYear
      actual.getMonth is expectedDate.getMonth
      actual.getYear is expectedDate.getYear
    }
  }

  "truncatedToDay" should {
    "truncate the timestamp to current day" in {
      val timestamp = ts("2022-05-18T14:06:43.268Z")
      truncatedToDay(timestamp) is ts("2022-05-18T00:00:00.000Z")
    }
  }

  "truncatedToHour" should {
    "truncate the timestamp to current hour" in {
      val timestamp = ts("2022-05-18T14:06:43.268Z")
      truncatedToHour(timestamp) is ts("2022-05-18T14:00:00.000Z")
    }
  }

  "buildTimestampRange" should {
    "build the correct ranges" in {

      buildTimestampRange(t(0), t(5), s(1)) is
        ArraySeq(r(0, 1), r(2, 3), r(4, 5))

      buildTimestampRange(t(0), t(5), s(2)) is
        ArraySeq(r(0, 2), r(3, 5))

      buildTimestampRange(t(0), t(6), s(2)) is
        ArraySeq(r(0, 2), r(3, 5), r(6, 6))

      buildTimestampRange(t(0), t(7), s(2)) is
        ArraySeq(r(0, 2), r(3, 5), r(6, 7))

      buildTimestampRange(t(1), t(1), s(1)) is
        ArraySeq(r(1, 1))

      buildTimestampRange(t(1), t(0), s(1)) is
        ArraySeq.empty

      buildTimestampRange(t(0), t(1), s(0)) is
        ArraySeq.empty
    }
  }

  "buildTimeStampRange" should {
    "return valid timestamp ranges" when {
      "local timestamp is behind remote timestamp" in {
        val actual =
          TimeUtil
            .buildTimeStampRange(
              step = Duration.ofMillisUnsafe(10),
              backStep = Duration.ofMillisUnsafe(5),
              localTs = TimeStamp.unsafe(20), // local is behind remote
              remoteTs = TimeStamp.unsafe(40) // remote is ahead
            )
            .success
            .value

        val expected =
          ArraySeq(
            (TimeStamp.unsafe(16), TimeStamp.unsafe(26)),
            (TimeStamp.unsafe(27), TimeStamp.unsafe(37)),
            (TimeStamp.unsafe(38), TimeStamp.unsafe(41))
          )

        expected is actual
      }
    }

    "return failure" when {
      "local timestamp is ahead of remote timestamp" in {
        forAll(Gen.posNum[Long], Gen.posNum[Short]) { case (timestamp, aheadBy) =>
          val localTs = TimeStamp.unsafe(
            timestamp + (aheadBy max 1)
          ) // max 1 so that local is ahead by at least 1
          val remoteTs = TimeStamp.unsafe(timestamp)

          TimeUtil
            .buildTimeStampRange(
              step = Duration.ofMillisUnsafe(10),
              backStep = Duration.ofMillisUnsafe(5),
              localTs = localTs,
              remoteTs = remoteTs
            )
            .failure
            .exception is RemoteTimeStampIsBeforeLocal(localTs, remoteTs)
        }
      }
    }
  }

  "buildTimeStampRangeOption" should {
    "return None" when {
      "local timestamp is None" in {
        forAll(Gen.posNum[Long], Gen.posNum[Long], timestampGen) {
          case (step, backStep, timeStamp) =>
            TimeUtil
              .buildTimeStampRangeOption(
                step = Duration.ofMillisUnsafe(step),
                backStep = Duration.ofMillisUnsafe(backStep),
                localTs = Some(timeStamp),
                remoteTs = None
              )
              .is(None)
        }
      }

      "remote timestamp is None" in {
        forAll(Gen.posNum[Long], Gen.posNum[Long], timestampGen) {
          case (step, backStep, timeStamp) =>
            TimeUtil
              .buildTimeStampRangeOption(
                step = Duration.ofMillisUnsafe(step),
                backStep = Duration.ofMillisUnsafe(backStep),
                localTs = None,
                remoteTs = Some(timeStamp)
              )
              .is(None)
        }
      }
    }
  }

  def ts(str: String): TimeStamp =
    TimeStamp.unsafe(Instant.parse(str).toEpochMilli)
  def t(l: Long)            = TimeStamp.unsafe(l)
  def s(l: Long)            = Duration.ofMillisUnsafe(l)
  def r(l1: Long, l2: Long) = (t(l1), t(l2))
}
