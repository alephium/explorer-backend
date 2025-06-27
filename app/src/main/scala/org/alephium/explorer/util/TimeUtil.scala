// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.util

import java.time.{Instant, LocalDate, OffsetTime, ZonedDateTime}
import java.time.temporal.ChronoUnit

import scala.annotation.tailrec
import scala.collection.immutable.ArraySeq
import scala.util.{Failure, Success, Try}

import org.alephium.explorer.error.ExplorerError.RemoteTimeStampIsBeforeLocal
import org.alephium.util.{Duration, TimeStamp}

object TimeUtil {

  /** Convert's [[java.time.OffsetTime]] to [[java.time.ZonedDateTime]] in the same zone */
  @inline def toZonedDateTime(time: OffsetTime): ZonedDateTime =
    time.atDate(LocalDate.now(time.getOffset)).toZonedDateTime

  @inline def toInstant(timestamp: TimeStamp): Instant =
    Instant.ofEpochMilli(timestamp.millis)

  def truncatedToDay(timestamp: TimeStamp): TimeStamp =
    mapInstant(timestamp)(_.truncatedTo(ChronoUnit.DAYS))

  def truncatedToHour(timestamp: TimeStamp): TimeStamp =
    mapInstant(timestamp)(_.truncatedTo(ChronoUnit.HOURS))

  def truncatedToWeek(timestamp: TimeStamp): TimeStamp =
    mapInstant(timestamp)(_.truncatedTo(ChronoUnit.WEEKS))

  private def mapInstant(timestamp: TimeStamp)(f: Instant => Instant): TimeStamp = {
    val instant = toInstant(timestamp)
    TimeStamp
      .unsafe(
        f(instant).toEpochMilli
      )
  }

  def buildTimestampRange(
      localTs: TimeStamp,
      remoteTs: TimeStamp,
      step: Duration
  ): ArraySeq[(TimeStamp, TimeStamp)] = {
    @tailrec
    def rec(
        l: TimeStamp,
        seq: ArraySeq[(TimeStamp, TimeStamp)]
    ): ArraySeq[(TimeStamp, TimeStamp)] = {
      val next = l + step
      if (next.isBefore(remoteTs)) {
        rec(next.plusMillisUnsafe(1), seq :+ ((l, next)))
      } else if (l == remoteTs) {
        seq :+ ((remoteTs, remoteTs))
      } else {
        seq :+ ((l, remoteTs))
      }
    }

    if (remoteTs.millis < localTs.millis || step == Duration.zero) {
      ArraySeq.empty
    } else {
      rec(localTs, ArraySeq.empty)
    }
  }

  /** Returns timestamp ranges built via [[buildTimestampRange]].
    *
    * @return
    *   \- Valid ranges if localTs is greater than remoteTs.
    *   - [[org.alephium.explorer.error.ExplorerError.RemoteTimeStampIsBeforeLocal]] If inputs are
    *     invalid.
    *   - Empty ranges if either one of the TimeStamps are empty.
    */
  def buildTimeStampRangeOrEmpty(
      step: Duration,
      backStep: Duration,
      localTs: Option[TimeStamp],
      remoteTs: Option[TimeStamp]
  ): Try[ArraySeq[(TimeStamp, TimeStamp)]] =
    buildTimeStampRangeOption(step, backStep, localTs, remoteTs) match {
      case None         => Success(ArraySeq.empty)
      case Some(result) => result
    }

  /** @see [[buildTimeStampRangeOrEmpty]] */
  def buildTimeStampRangeOption(
      step: Duration,
      backStep: Duration,
      localTs: Option[TimeStamp],
      remoteTs: Option[TimeStamp]
  ): Option[Try[ArraySeq[(TimeStamp, TimeStamp)]]] =
    localTs.zip(remoteTs) map { case (localTs, remoteTs) =>
      buildTimeStampRange(step, backStep, localTs, remoteTs)
    }

  /** @see [[buildTimeStampRangeOrEmpty]] */
  def buildTimeStampRange(
      step: Duration,
      backStep: Duration,
      localTs: TimeStamp,
      remoteTs: TimeStamp
  ): Try[ArraySeq[(TimeStamp, TimeStamp)]] = {
    val localTsPlus1  = localTs.plusMillisUnsafe(1)
    val remoteTsPlus1 = remoteTs.plusMillisUnsafe(1)
    if (remoteTsPlus1.isBefore(localTsPlus1)) {
      Failure(RemoteTimeStampIsBeforeLocal(localTs = localTs, remoteTs = remoteTs))
    } else {
      val localTsBackStep = localTsPlus1.minusUnsafe(backStep)
      val range           = buildTimestampRange(localTsBackStep, remoteTsPlus1, step)
      Success(range)
    }
  }
}
