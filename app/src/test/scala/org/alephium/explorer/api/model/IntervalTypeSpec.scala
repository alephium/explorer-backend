// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.api.model

import scala.concurrent.Future

import org.scalacheck.Gen

import org.alephium.api.ApiError
import org.alephium.api.model.TimeInterval
import org.alephium.explorer.AlephiumFutureSpec
import org.alephium.explorer.GenApiModel.intervalTypeGen
import org.alephium.util.{Duration, TimeStamp}

class IntervalTypeSpec() extends AlephiumFutureSpec {
  "IntervalType" should {
    "validate TimeInterval" in {
      val now = TimeStamp.now()
      forAll(intervalTypeGen, Gen.choose(1L, 10L)) { case (intervalType, amount) =>
        val duration     = (intervalType.duration * amount).get
        val to           = now + duration
        val timeInterval = TimeInterval(now, to)

        IntervalType
          .validateTimeInterval(timeInterval, intervalType, duration, duration, duration)(
            Future.successful(())
          )
          .futureValue is Right(())

        val shorterDuration = (duration - (Duration.ofMillisUnsafe(1))).get

        IntervalType
          .validateTimeInterval(
            timeInterval,
            intervalType,
            shorterDuration,
            shorterDuration,
            shorterDuration
          )(
            Future.successful(())
          )
          .futureValue is Left(
          ApiError.BadRequest(s"Time span cannot be greater than $shorterDuration")
        )
      }
    }
  }
}
