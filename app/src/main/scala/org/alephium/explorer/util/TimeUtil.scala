// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.util

import java.time.{Instant, LocalDate, OffsetTime, ZonedDateTime, ZoneOffset}
import java.time.temporal.{ChronoUnit, TemporalAdjusters}

import scala.annotation.tailrec
import scala.collection.immutable.ArraySeq
import scala.util.{Failure, Success, Try}

import org.alephium.explorer.api.model.IntervalType
import org.alephium.explorer.error.ExplorerError.RemoteTimeStampIsBeforeLocal
import org.alephium.util.{Duration, TimeStamp}

object TimeUtil {

  // scalastyle:off magic.number
  val hourlyStepBack: Duration  = Duration.ofHoursUnsafe(2)
  val dailyStepBack: Duration   = Duration.ofDaysUnsafe(1)
  val weeklyStepBack: Duration  = Duration.ofDaysUnsafe(7)
  val monthlyStepBack: Duration = Duration.ofDaysUnsafe(31)
  // scalastyle:on magic.number

  /** Convert's [[java.time.OffsetTime]] to [[java.time.ZonedDateTime]] in the same zone */
  @inline def toZonedDateTime(time: OffsetTime): ZonedDateTime =
    time.atDate(LocalDate.now(time.getOffset)).toZonedDateTime

  @inline def toInstant(timestamp: TimeStamp): Instant =
    Instant.ofEpochMilli(timestamp.millis)

  def truncateTime(timestamp: TimeStamp, intervalType: IntervalType): TimeStamp =
    intervalType match {
      case IntervalType.Hourly  => truncatedToHour(timestamp)
      case IntervalType.Daily   => truncatedToDay(timestamp)
      case IntervalType.Weekly  => truncatedToWeek(timestamp)
      case IntervalType.Monthly => truncatedToMonth(timestamp)
    }

  def truncatedToDay(timestamp: TimeStamp): TimeStamp =
    mapInstant(timestamp)(_.truncatedTo(ChronoUnit.DAYS))

  def truncatedToHour(timestamp: TimeStamp): TimeStamp =
    mapInstant(timestamp)(_.truncatedTo(ChronoUnit.HOURS))

  def truncatedToWeek(timestamp: TimeStamp): TimeStamp =
    mapInstant(timestamp) { instant =>
      instant
        .atZone(ZoneOffset.UTC)
        .`with`(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        .truncatedTo(ChronoUnit.DAYS)
        .toInstant
    }

  def truncatedToMonth(timestamp: TimeStamp): TimeStamp = {
    val result = mapInstant(timestamp)(
      _.atZone(ZoneOffset.UTC)
        .withDayOfMonth(1)
        .truncatedTo(ChronoUnit.DAYS)
        .toInstant()
    )
    result

  }

  private def mapInstant(timestamp: TimeStamp)(f: Instant => Instant): TimeStamp =
    TimeStamp.unsafe(f(toInstant(timestamp)).toEpochMilli)

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

  @SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
  def getTimeRanges(
      histTs: TimeStamp,
      latestTxTs: TimeStamp,
      intervalType: IntervalType
  ): ArraySeq[(TimeStamp, TimeStamp)] = {

    val oneMillis = Duration.ofMillisUnsafe(1)
    val start     = truncateTime(histTs, intervalType)
    val end       = truncateTime(latestTxTs, intervalType).minusUnsafe(oneMillis)

    if (start == end || end.isBefore(start)) {
      ArraySeq.empty
    } else {
      intervalType match {
        case IntervalType.Monthly =>
          // Use calendar-aware month boundaries to handle 28/29/30/31 day months
          buildMonthlyTimestampRange(start, end)

        case _ =>
          val step = (intervalType match {
            case IntervalType.Hourly => Duration.ofHoursUnsafe(1)
            case IntervalType.Daily  => Duration.ofDaysUnsafe(1)
            case IntervalType.Weekly => Duration.ofDaysUnsafe(7)
            case IntervalType.Monthly =>
              throw new IllegalArgumentException("Monthly should be handled separately")
          }).-(oneMillis).get

          buildTimestampRange(start, end, step)
      }
    }
  }

  /** Builds monthly ranges using calendar-aware boundaries (handles 28/29/30/31 day months). */
  private def buildMonthlyTimestampRange(
      start: TimeStamp,
      end: TimeStamp
  ): ArraySeq[(TimeStamp, TimeStamp)] = {
    val oneMillis = Duration.ofMillisUnsafe(1)

    def toYearMonth(ts: TimeStamp): java.time.YearMonth =
      java.time.YearMonth.from(
        java.time.Instant.ofEpochMilli(ts.millis).atZone(java.time.ZoneOffset.UTC)
      )

    def yearMonthToTs(ym: java.time.YearMonth): TimeStamp =
      TimeStamp.unsafe(
        ym.atDay(1)
          .atStartOfDay(java.time.ZoneOffset.UTC)
          .toInstant
          .toEpochMilli
      )

    val startYearMonth = toYearMonth(start)
    val endYearMonth   = toYearMonth(end)

    // Number of months between start and end (inclusive)
    val monthCount =
      startYearMonth.until(endYearMonth, java.time.temporal.ChronoUnit.MONTHS).toInt + 1

    ArraySeq
      .tabulate(monthCount) { i =>
        val rangeStart = yearMonthToTs(startYearMonth.plusMonths(i.toLong))
        val rangeEnd =
          yearMonthToTs(startYearMonth.plusMonths((i + 1).toLong)).minusUnsafe(oneMillis)
        (rangeStart, rangeEnd)
      }
  }

  /*
   * Step back a bit in time to recompute some latest values,
   * to be sure we didn't miss some unsynced blocks
   */
  def stepBack(timestamp: TimeStamp, intervalType: IntervalType): TimeStamp =
    intervalType match {
      case IntervalType.Hourly  => timestamp.minusUnsafe(hourlyStepBack)
      case IntervalType.Daily   => timestamp.minusUnsafe(dailyStepBack)
      case IntervalType.Weekly  => timestamp.minusUnsafe(weeklyStepBack)
      case IntervalType.Monthly => timestamp.minusUnsafe(monthlyStepBack)
    }

}
