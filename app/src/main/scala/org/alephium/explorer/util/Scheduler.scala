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

import java.time.{OffsetTime, ZonedDateTime}
import java.util.{Timer, TimerTask}

import scala.annotation.tailrec
import scala.concurrent._
import scala.concurrent.duration._
import scala.util.{Failure, Success}

import com.typesafe.scalalogging.StrictLogging

import org.alephium.explorer.util.Scheduler.scheduleTime

object Scheduler extends StrictLogging {

  /**
    * Creates a Scheduler.
    *
    * @param name     Name of the associated thread
    * @param isDaemon `true` if the associated thread should run as a daemon
    */
  @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
  def apply(name: String, isDaemon: Boolean = true): Scheduler =
    new Scheduler(
      name       = name,
      timer      = new Timer(name, isDaemon),
      terminated = false
    )

  /**
    * Calculate time left to schedule for the input [[java.time.ZonedDateTime]].
    * If the time is in the past then it schedules for tomorrow.
    *
    * @param scheduleAt Time to schedule at.
    *
    * @return Time left until next schedule.
    */
  @tailrec
  def scheduleTime(scheduleAt: ZonedDateTime): FiniteDuration = {
    import java.time.Duration

    val timeLeft = Duration.between(ZonedDateTime.now(scheduleAt.getZone), scheduleAt)

    if (timeLeft.isNegative) { //time is in the past, schedule for tomorrow.
      val daysBehind = Math.abs(timeLeft.toDays) + 1
      logger.trace(s"Scheduled time is $daysBehind.days behind.")
      scheduleTime(scheduleAt.plusDays(daysBehind))
    } else { //time is in the future. Good!
      val nextSchedule = timeLeft.toNanos.nanos
      //calculate first schedule using today's date.
      logger.debug(s"Scheduled task after ${nextSchedule.toSeconds}.seconds")
      nextSchedule
    }
  }
}

class Scheduler private (name: String, timer: Timer, @volatile private var terminated: Boolean)
    extends AutoCloseable
    with StrictLogging {

  /**
    * Schedules the block after a delay.
    *
    * Every other function is just a combinator to build more functionality
    * on top of this function.
    */
  def scheduleOnce[T](delay: FiniteDuration)(block: => Future[T]): Future[T] = {
    val promise = Promise[T]()

    val task =
      new TimerTask {
        @SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
        def run(): Unit =
          promise.completeWith(block)
      }

    timer.schedule(task, delay.toMillis max 0)
    promise.future
  }

  /** Schedule block at given interval */
  def scheduleLoop[T](interval: FiniteDuration)(block: => Future[T])(
      implicit ec: ExecutionContext): Future[T] =
    scheduleLoop(interval, interval)(block)

  /** Schedules the block at given `loopInterval` with the first schedule at `firstInterval` */
  @SuppressWarnings(Array("org.wartremover.warts.Recursion", "org.wartremover.warts.Overloading"))
  def scheduleLoop[T](firstInterval: FiniteDuration, loopInterval: FiniteDuration)(
      block: => Future[T])(implicit ec: ExecutionContext): Future[T] = {
    val initial = scheduleOnce(firstInterval)(block)

    initial onComplete {
      case Failure(exception) =>
        //Log the failure.
        logger.error(s"Scheduler '$name': Failed executing task", exception)
        scheduleLoop(loopInterval, loopInterval)(block)

      case Success(_) =>
        if (!terminated) {
          scheduleLoop(loopInterval, loopInterval)(block)
        }
    }

    initial
  }

  /**
    * Schedules daily, starting from the given [[java.time.ZonedDateTime]].
    *
    * If the time is in the past (eg: 1PM when now is 2PM) then the
    * schedule occurs for tomorrow.
    */
  @SuppressWarnings(Array("org.wartremover.warts.Recursion", "org.wartremover.warts.Overloading"))
  def scheduleDailyAt[T](at: ZonedDateTime)(block: => Future[T])(
      implicit ec: ExecutionContext): Unit =
    scheduleOnce(scheduleTime(at))(block) onComplete {
      case Failure(exception) =>
        //Log the failure.
        logger.error(s"Scheduler '$name': Failed executing task", exception)
        scheduleDailyAt(at)(block)

      case Success(_) =>
        if (!terminated) {
          scheduleDailyAt(at)(block)
        }
    }

  /**
    * Schedules daily, starting from today.
    */
  @SuppressWarnings(Array("org.wartremover.warts.Overloading"))
  def scheduleDailyAt[T](at: OffsetTime)(block: => Future[T])(implicit ec: ExecutionContext): Unit =
    scheduleDailyAt(TimeUtil.toZonedDateTime(at))(block)

  @SuppressWarnings(Array("org.wartremover.warts.Overloading"))
  def scheduleLoopFlatMap[A, B](interval: FiniteDuration)(init: => Future[A])(
      block: A => Future[B])(implicit ec: ExecutionContext): Future[B] =
    scheduleLoopFlatMap(interval, interval)(init)(block)

  /**
    * Similar to `scheduleLoop` but invokes `init` block only once and
    * makes that init value available to `block` for all future schedules.
    */
  def scheduleLoopFlatMap[A, B](firstInterval: FiniteDuration, loopInterval: FiniteDuration)(
      init: => Future[A])(block: A => Future[B])(implicit ec: ExecutionContext): Future[B] = {
    //None if init has not yet been invoked else Some(A)
    @volatile var initializerResult: Option[A] = None

    scheduleLoop(
      firstInterval = firstInterval,
      loopInterval  = loopInterval
    ) {
      //flatMap init onto block
      initializerResult match {
        case Some(value) =>
          //init was already invoked, invoke block.
          block(value)

        case None =>
          //init not invoked, invoke not and flatMap onto block
          init flatMap { result =>
            initializerResult = Some(result)
            block(result)
          }
      }
    }
  }

  override def close(): Unit = {
    terminated = true
    timer.cancel()
  }
}
