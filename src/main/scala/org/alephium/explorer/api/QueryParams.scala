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

package org.alephium.explorer.api

import sttp.tapir._

import org.alephium.explorer.api.Codecs.timestampTapirCodec
import org.alephium.explorer.api.model.TimeInterval
import org.alephium.util.{Duration, TimeStamp}

trait QueryParams {

  val maxTimeInterval: Duration = Duration.ofMinutes(10).getOrElse(Duration.unsafe(0))

  private def tsQuery(name: String) = query[TimeStamp](name).description("Unix epoch timestamp")

  val timeIntervalQuery: EndpointInput[TimeInterval] =
    tsQuery("from-ts")
      .and(tsQuery("to-ts"))
      .validate(
        Validator.custom({
          case (from, to) =>
            if (from > to) {
              List(ValidationError.Custom((from, to), "`from-ts` must be before `to-ts`"))
            } else {
              List.empty
            }
        })
      )
      .validate(
        Validator.custom({
          case (from, to) =>
            val interval = scala.math.abs(to.millis - from.millis)
            if (interval > maxTimeInterval.millis) {
              List(ValidationError.Custom(interval,
                                          s"maximum interval is ${maxTimeInterval.millis}ms"))
            } else {
              List.empty
            }
        })
      )
      .map({ case (from, to) => TimeInterval.unsafe(from, to) })(timeInterval =>
        (timeInterval.from, timeInterval.to))
}
