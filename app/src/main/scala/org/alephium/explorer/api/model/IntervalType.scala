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

import scala.collection.immutable.ArraySeq

import upickle.core.Abort

import org.alephium.json.Json._

sealed trait IntervalType {
  def value: Int
  def string: String
}

object IntervalType {
  case object Hourly extends IntervalType {
    val value: Int     = 0
    val string: String = "hourly"
  }
  case object Daily extends IntervalType {
    val value: Int     = 1
    val string: String = "daily"
  }

  val all: ArraySeq[IntervalType] = ArraySeq(Hourly: IntervalType, Daily: IntervalType)

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  implicit val reader: Reader[IntervalType] =
    StringReader.map {
      validate(_) match {
        case Some(intervalType) => intervalType
        case None               => throw new Abort("Cannot decode time-step, expected one of: hourly, daily")
      }
    }

  def validate(str: String): Option[IntervalType] =
    str match {
      case Hourly.string => Some(Hourly)
      case Daily.string  => Some(Daily)
      case _             => None
    }

  implicit val writer: Writer[IntervalType] =
    StringWriter.comap(_.string)

  def unsafe(int: Int): IntervalType = {
    int match {
      case IntervalType.Daily.value  => IntervalType.Daily
      case IntervalType.Hourly.value => IntervalType.Hourly
    }
  }
}
