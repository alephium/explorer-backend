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

import java.time.{Instant, LocalDateTime, LocalTime, ZoneId, ZoneOffset}

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import org.alephium.explorer.AlephiumSpec._

class TimeUtilSpec extends AnyWordSpec with Matchers {

  "toLocalFromUTC" should {
    "convert UTC LocalDateTime to system's LocalDateTime" in {
      val millis = Instant.ofEpochMilli(System.currentTimeMillis())

      val utc   = LocalDateTime.ofInstant(millis, ZoneOffset.UTC)
      val local = LocalDateTime.ofInstant(millis, ZoneId.systemDefault())

      //convert utc to local
      TimeUtil.toLocalFromUTC(utc) is local
    }
  }

  "toLocalDateTimeNow" should {
    "convert LocalTime to system's LocalDateTime" in {
      val millis = Instant.ofEpochMilli(System.currentTimeMillis())

      val utc   = LocalTime.ofInstant(millis, ZoneOffset.UTC)
      val local = LocalDateTime.ofInstant(millis, ZoneId.systemDefault())

      //convert utc to local
      TimeUtil.toLocalDateTimeNow(utc) is local
    }
  }

}
