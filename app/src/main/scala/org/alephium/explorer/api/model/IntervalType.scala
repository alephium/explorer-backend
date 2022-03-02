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

package org.alephium.explorer.api.model

import upickle.core.Abort

import org.alephium.json.Json._

sealed trait IntervalType {
  def value: Int
}

object IntervalType {
  case object Hourly extends IntervalType {
    val value: Int = 0
  }
  case object Daily extends IntervalType {
    val value: Int = 1
  }

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  implicit val reader: Reader[IntervalType] =
    StringReader.map {
      case "hourly" => Hourly
      case "daily"  => Daily
      case _ =>
        throw new Abort("Cannot decode time-step, expected one of: hourly, daily")
    }

  implicit val writer: Writer[IntervalType] =
    StringWriter.comap {
      case Hourly => "hourly"
      case Daily  => "daily"
    }

  def unsafe(int: Int): IntervalType = {
    int match {
      case IntervalType.Daily.value  => IntervalType.Daily
      case IntervalType.Hourly.value => IntervalType.Hourly
    }
  }
}
