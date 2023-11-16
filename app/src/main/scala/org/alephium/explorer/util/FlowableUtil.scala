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

import java.math.BigInteger

import scala.concurrent.Future
import scala.jdk.CollectionConverters._
import scala.jdk.FutureConverters._

import io.reactivex.rxjava3.core.Flowable
import io.vertx.core.buffer.Buffer

import org.alephium.explorer.api.model._
import org.alephium.explorer.util.TimeUtil
import org.alephium.util.{TimeStamp, U256}

object FlowableUtil {
  def amountHistoryToJsonFlowable(history: Flowable[(BigInteger, TimeStamp)]): Flowable[Buffer] = {
    history
      .concatMap { case (diff, to: TimeStamp) =>
        val str = s"""[${to.millis},"$diff"]"""
        Flowable.just(str, ",")
      }
      .skipLast(1) // Removing latest "," it replace the missing `intersperse`
      .startWith(Flowable.just("""{"amountHistory":["""))
      .concatWith(Flowable.just("]}"))
      .map(Buffer.buffer)
  }

  def getAmountHistory(
      from: TimeStamp,
      to: TimeStamp,
      intervalType: IntervalType,
      paralellism: Int
  )(getInOutAmount: (TimeStamp, TimeStamp) => Future[(U256, U256, TimeStamp)]): Flowable[Buffer] = {
    val timeranges = TimeUtil.amountHistoryTimeRanges(from, to, intervalType)
    val amountHistory = Flowable
      .fromIterable(timeranges.asJava)
      .concatMapEager(
        { case (from: TimeStamp, to: TimeStamp) =>
          Flowable.fromCompletionStage(getInOutAmount(from, to).asJava)
        },
        paralellism,
        1
      )
      .filter {
        // Ignore time ranges without data
        case (in, out, _) => in != U256.Zero || out != U256.Zero
      }
      .scan(
        (BigInteger.ZERO, TimeStamp.zero),
        { (acc: (BigInteger, TimeStamp), next) =>
          val (sum, _)      = acc
          val (in, out, to) = next
          val diff          = out.v.subtract(in.v)
          val newSum        = sum.add(diff)
          (newSum, to)
        }
      )
      .skip(1) // Drop first elem which is the seed of the scan (0,0)

    amountHistoryToJsonFlowable(
      amountHistory
    )
  }
}
