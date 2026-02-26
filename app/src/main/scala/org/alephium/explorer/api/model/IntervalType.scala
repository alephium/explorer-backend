// Copyright (c) Alephium
// SPDX-License-Identifier: LGPL-3.0-only

package org.alephium.explorer.api.model

import java.time.temporal.ChronoUnit

import scala.collection.immutable.ArraySeq
import scala.concurrent.{ExecutionContext, Future}

import sttp.model.StatusCode
import upickle.core.Abort

import org.alephium.api.ApiError
import org.alephium.api.model.TimeInterval
import org.alephium.json.Json._
import org.alephium.util.Duration

sealed trait IntervalType {
  def value: Int
  def string: String
  def chronoUnit: ChronoUnit
  def duration: Duration
}

//scalastyle:off magic.number
object IntervalType {
  case object Hourly extends IntervalType {
    val value: Int             = 0
    val string: String         = "hourly"
    val chronoUnit: ChronoUnit = ChronoUnit.HOURS
    val duration: Duration     = Duration.ofHoursUnsafe(1)
    override def toString()    = string
  }

  case object Daily extends IntervalType {
    val value: Int             = 1
    val string: String         = "daily"
    val chronoUnit: ChronoUnit = ChronoUnit.DAYS
    val duration: Duration     = Duration.ofDaysUnsafe(1)
    override def toString()    = string
  }

  case object Weekly extends IntervalType {
    val value: Int             = 2
    val string: String         = "weekly"
    val chronoUnit: ChronoUnit = ChronoUnit.WEEKS
    val duration: Duration     = Duration.ofDaysUnsafe(7)
    override def toString()    = string
  }

  case object Monthly extends IntervalType {
    val value: Int             = 3
    val string: String         = "monthly"
    val chronoUnit: ChronoUnit = ChronoUnit.WEEKS
    val duration: Duration     = Duration.ofDaysUnsafe(31)
    override def toString()    = string
  }

  val all: ArraySeq[IntervalType] =
    ArraySeq(Hourly: IntervalType, Daily: IntervalType, Weekly: IntervalType, Monthly: IntervalType)

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  implicit val reader: Reader[IntervalType] =
    StringReader.map {
      validate(_) match {
        case Some(intervalType) => intervalType
        case None =>
          throw new Abort(
            s"Cannot decode time-step, expected one of: ${all.map(_.string).mkString(", ")}"
          )
      }
    }

  def validate(str: String): Option[IntervalType] =
    str match {
      case Hourly.string  => Some(Hourly)
      case Daily.string   => Some(Daily)
      case Weekly.string  => Some(Weekly)
      case Monthly.string => Some(Monthly)
      case _              => None
    }

  def validateSubset(str: String)(subset: Set[IntervalType]): Option[IntervalType] =
    str match {
      case Hourly.string if subset.contains(Hourly)   => Some(Hourly)
      case Daily.string if subset.contains(Daily)     => Some(Daily)
      case Weekly.string if subset.contains(Weekly)   => Some(Weekly)
      case Monthly.string if subset.contains(Monthly) => Some(Monthly)
      case _                                          => None
    }

  implicit val writer: Writer[IntervalType] =
    StringWriter.comap(_.string)

  def unsafe(int: Int): IntervalType = {
    int match {
      case IntervalType.Hourly.value  => IntervalType.Hourly
      case IntervalType.Daily.value   => IntervalType.Daily
      case IntervalType.Weekly.value  => IntervalType.Weekly
      case IntervalType.Monthly.value => IntervalType.Monthly
    }
  }

  @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
  def validateTimeInterval[A](
      timeInterval: TimeInterval,
      intervalType: IntervalType,
      maxHourlyTimeSpan: Duration,
      maxDailyTimeSpan: Duration,
      maxWeeklyTimeSpan: Duration,
      maxMonthlyTimeSpan: Duration
  )(
      contd: => Future[A]
  )(implicit executionContext: ExecutionContext): Future[Either[ApiError[_ <: StatusCode], A]] = {
    val timeSpan =
      intervalType match {
        case IntervalType.Hourly  => maxHourlyTimeSpan
        case IntervalType.Daily   => maxDailyTimeSpan
        case IntervalType.Weekly  => maxWeeklyTimeSpan
        case IntervalType.Monthly => maxMonthlyTimeSpan
      }
    timeInterval.validateTimeSpan(timeSpan) match {
      case Left(error) => Future.successful(Left(error))
      case Right(_)    => contd.map(Right(_))
    }
  }
}
