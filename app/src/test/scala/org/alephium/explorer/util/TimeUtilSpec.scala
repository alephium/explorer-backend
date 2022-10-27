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

package org.alephium.explorer.util

import java.time.{Instant, LocalDateTime, OffsetTime, ZoneId}

import scala.collection.immutable.ArraySeq

import org.scalacheck.Gen
import org.scalatest.OptionValues._
import org.scalatest.TryValues._
import org.scalatest.matchers.should.Matchers

import org.alephium.explorer.AlephiumSpec
import org.alephium.explorer.GenCoreUtil._
import org.alephium.explorer.error.ExplorerError.RemoteTimeStampIsBeforeLocal
import org.alephium.explorer.util.TimeUtil._
import org.alephium.util.{Duration, TimeStamp}

class TimeUtilSpec extends AlephiumSpec with Matchers {

  "toZonedDateTime" should {
    "convert OffsetTime to ZonedDateTime with today's date" in {
      val zone         = ZoneId.of("Australia/Perth")
      val expectedDate = LocalDateTime.now(zone) //expected day/month/year
      val expectedTime = OffsetTime.now(zone) //expected hour/minute/day

      val actual = toZonedDateTime(expectedTime) //actual ZonedDateTime

      //check time
      actual.getHour is expectedTime.getHour
      actual.getMinute is expectedTime.getMinute
      actual.getSecond is expectedTime.getSecond
      //check year
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
        ArraySeq(r(0, 2), r(3, 5), r(6, 7))

      buildTimestampRange(t(0), t(7), s(2)) is
        ArraySeq(r(0, 2), r(3, 5), r(6, 7))

      buildTimestampRange(t(1), t(1), s(1)) is
        ArraySeq.empty

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
              step     = Duration.ofMillisUnsafe(10),
              backStep = Duration.ofMillisUnsafe(5),
              localTs  = TimeStamp.unsafe(20), //local is behind remote
              remoteTs = TimeStamp.unsafe(40) //remote is ahead
            )
            .success
            .value

        val expected =
          ArraySeq((TimeStamp.unsafe(16), TimeStamp.unsafe(26)),
                   (TimeStamp.unsafe(27), TimeStamp.unsafe(37)),
                   (TimeStamp.unsafe(38), TimeStamp.unsafe(41)))

        expected is actual
      }
    }

    "return failure" when {
      "local timestamp is ahead of remote timestamp" in {
        forAll(Gen.posNum[Long], Gen.posNum[Short]) {
          case (timestamp, aheadBy) =>
            val localTs  = TimeStamp.unsafe(timestamp + (aheadBy max 1)) //max 1 so that local is ahead by at least 1
            val remoteTs = TimeStamp.unsafe(timestamp)

            TimeUtil
              .buildTimeStampRange(step     = Duration.ofMillisUnsafe(10),
                                   backStep = Duration.ofMillisUnsafe(5),
                                   localTs  = localTs,
                                   remoteTs = remoteTs)
              .failure
              .exception is RemoteTimeStampIsBeforeLocal(localTs, remoteTs)
        }
      }
    }
  }

  "buildTimeStampRangeOption" should {
    "return None" when {
      "local timestamp is None" in {
        forAll(Gen.posNum[Long], Gen.posNum[Long], timestampGen, Gen.posNum[Int]) {
          case (step, backStep, timeStamp, numOfBlocks) =>
            TimeUtil
              .buildTimeStampRangeOption(step     = Duration.ofMillisUnsafe(step),
                                         backStep = Duration.ofMillisUnsafe(backStep),
                                         localTs  = Some((timeStamp, numOfBlocks)),
                                         remoteTs = None)
              .is(None)
        }
      }

      "remote timestamp is None" in {
        forAll(Gen.posNum[Long], Gen.posNum[Long], timestampGen, Gen.posNum[Int]) {
          case (step, backStep, timeStamp, numOfBlocks) =>
            TimeUtil
              .buildTimeStampRangeOption(step     = Duration.ofMillisUnsafe(step),
                                         backStep = Duration.ofMillisUnsafe(backStep),
                                         localTs  = None,
                                         remoteTs = Some((timeStamp, numOfBlocks)))
              .is(None)
        }
      }
    }

    "return total number of blocks (remoteNumOfBlocks - localNumOfBlocks)" when {
      "all inputs are valid" in {
        //Test: When all inputs are valid, NumOfBlocks should be (remoteNumOfBlocks - localNumOfBlocks)
        forAll(Gen.posNum[Long], Gen.posNum[Int], Gen.posNum[Int]) {
          case (timestamp, localNumOfBlocks, remoteNumOfBlocks) =>
            //localNumBlocks should be greater than or equal to remoteNumBlocks
            val localBlocks = remoteNumOfBlocks min localNumOfBlocks

            //Only test for numOfBlocks. It should be (remote - local)
            val (_, numOfBlocks) =
              TimeUtil
                .buildTimeStampRangeOption(
                  step     = Duration.ofMillisUnsafe(0),
                  backStep = Duration.ofMillisUnsafe(0),
                  localTs  = Some((TimeStamp.unsafe(timestamp), localBlocks)),
                  remoteTs = Some((TimeStamp.unsafe(timestamp + 1), remoteNumOfBlocks))
                )
                .value
                .success
                .value

            //Num of blocks returned should be (remote - local)
            numOfBlocks is (remoteNumOfBlocks - localBlocks)
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
